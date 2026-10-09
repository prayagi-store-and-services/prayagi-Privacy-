package com.example.siteblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Settings kept on the phone only. The names of visited sites are never stored. */
class SiteBlockPrefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("site_block", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = p.getBoolean("enabled", false)
        set(v) { p.edit().putBoolean("enabled", v).apply() }
    var allow: Set<String>
        get() = p.getStringSet("allow", emptySet()) ?: emptySet()
        set(v) { p.edit().putStringSet("allow", v).apply() }
    var lastError: String?
        get() = p.getString("last_error", null)
        set(v) { p.edit().putString("last_error", v).apply() }
    val blockedTotal: Long get() = p.getLong("blocked_total", 0L)
    fun blockedToday(): Long = if (p.getString("day", "") == today()) p.getLong("blocked_today", 0L) else 0L
    fun addBlocked(n: Long) {
        val day = today()
        val sameDay = p.getString("day", "") == day
        p.edit().putString("day", day)
            .putLong("blocked_today", (if (sameDay) p.getLong("blocked_today", 0L) else 0L) + n)
            .putLong("blocked_total", p.getLong("blocked_total", 0L) + n).apply()
    }
    private fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
}

/** Loads the bundled list (assets/blocklist/domains.txt.gz) once and keeps a fast hash copy in the app's private folder. */
object SiteBlockLoader {
    const val ASSET = "blocklist/domains.txt.gz"
    private const val CACHE = "blocklist_v1.bin"
    private const val STAMP = "blocklist_v1.stamp"

    fun load(context: Context): BlockSet? {
        val dir = context.filesDir
        val cache = File(dir, CACHE)
        val stamp = File(dir, STAMP)
        val present = try { context.assets.open(ASSET).use { true } } catch (e: Exception) { false }
        if (!present) return null
        val wanted = "app" + com.example.BuildConfig.VERSION_CODE
        if (cache.exists() && stamp.exists() && stamp.readText() == wanted) {
            val cached = try { cache.inputStream().use { BlockSetFile.read(it) } } catch (e: Exception) { null }
            if (cached != null) return BlockSet(cached)
        }
        val hashes = context.assets.open(ASSET).use { ListParser.hashesFrom(it, gzip = true) }
        if (hashes.isEmpty()) return null
        try {
            val tmp = File(dir, "$CACHE.tmp")
            tmp.outputStream().use { BlockSetFile.write(it, hashes) }
            tmp.renameTo(cache)
            stamp.writeText(wanted)
        } catch (e: Exception) { /* cache is only a speed-up */ }
        return BlockSet(hashes)
    }
}

/**
 * Local DNS filter. The phone is told its DNS server is a made-up address that only this app answers; only DNS
 * questions go through here. A blocked name gets "does not exist"; every other question is passed unchanged to the
 * phone's own DNS server. Nothing else is read or sent, and no visited names are saved.
 */
class SiteBlockVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private var worker: Thread? = null
    private val pool = Executors.newFixedThreadPool(6)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            SiteBlockPrefs(this).enabled = false
            stopBlocking()
            stopSelf()
            return START_NOT_STICKY
        }
        showForeground()
        if (!running) startBlocking()
        return START_STICKY
    }

    private fun showForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Site blocking", NotificationManager.IMPORTANCE_LOW))
        }
        val open = packageManager.getLaunchIntentForPackage(packageName)
        val pi = if (open != null) PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE) else null
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Site blocking is on")
            .setContentText("Adult and gambling sites are blocked on this phone")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true).setContentIntent(pi).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIF_ID, n)
    }

    private fun phoneDnsServer(): InetAddress? {
        return try {
            val cm = getSystemService(ConnectivityManager::class.java)
            val net = cm.activeNetwork ?: return null
            cm.getLinkProperties(net)?.dnsServers?.firstOrNull { it is Inet4Address }
        } catch (e: Exception) { null }
    }

    private fun startBlocking() {
        val prefs = SiteBlockPrefs(this)
        val set = SiteBlockLoader.load(this)
        if (set == null) { fail(prefs, "The block list could not be loaded."); return }
        // Read the phone's own DNS server before our VPN becomes the active network.
        val upstream = phoneDnsServer()
        if (upstream == null) { fail(prefs, "No DNS server was found on this network, so blocking was not started."); return }
        val matcher = SiteMatcher(set, prefs.allow)
        val fd = try {
            Builder().setSession("SensorGuard site blocking")
                .addAddress(VPN_ADDR, 32)
                .addDnsServer(FAKE_DNS)
                .addRoute(FAKE_DNS, 32)
                .setBlocking(true)
                .establish()
        } catch (e: Exception) { null }
        if (fd == null) { fail(prefs, "Android did not allow the VPN. Please allow it when asked."); return }
        tun = fd
        running = true
        prefs.lastError = null
        worker = Thread({ loop(fd, matcher, upstream, prefs) }, "siteblock-loop").also { it.start() }
    }

    private fun fail(prefs: SiteBlockPrefs, msg: String) {
        prefs.lastError = msg
        prefs.enabled = false
        stopBlocking()
        stopSelf()
    }

    private fun loop(fd: ParcelFileDescriptor, matcher: SiteMatcher, upstream: InetAddress, prefs: SiteBlockPrefs) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        val pending = AtomicLong(0)
        var lastFlush = System.currentTimeMillis()
        try {
            while (running) {
                val n = input.read(buf)
                if (n <= 0) { Thread.sleep(20); continue }
                val q = DnsPacket.parse(buf, n)
                if (q != null) {
                    if (matcher.isBlocked(q.name)) {
                        val answer = DnsPacket.reply(q, DnsPacket.nxdomain(q.dnsPayload))
                        synchronized(output) { output.write(answer) }
                        pending.incrementAndGet()
                    } else {
                        pool.execute { forward(q, upstream, output) }
                    }
                }
                val now = System.currentTimeMillis()
                if (pending.get() > 0 && now - lastFlush > 5000) {
                    prefs.addBlocked(pending.getAndSet(0)); lastFlush = now
                }
            }
        } catch (e: Exception) {
            // The tunnel was closed (turned off) or Android stopped it.
        } finally {
            val left = pending.getAndSet(0)
            if (left > 0) prefs.addBlocked(left)
        }
    }

    private fun forward(q: DnsQuery, upstream: InetAddress, output: FileOutputStream) {
        try {
            DatagramSocket().use { s ->
                protect(s) // keep our own question outside the tunnel
                s.soTimeout = 4000
                s.connect(upstream, 53)
                s.send(DatagramPacket(q.dnsPayload, q.dnsPayload.size))
                val rb = ByteArray(4096)
                val dp = DatagramPacket(rb, rb.size)
                s.receive(dp)
                val answer = DnsPacket.reply(q, rb.copyOf(dp.length))
                synchronized(output) { output.write(answer) }
            }
        } catch (e: Exception) {
            // No answer: the app that asked will retry on its own.
        }
    }

    private fun stopBlocking() {
        running = false
        try { tun?.close() } catch (e: Exception) { }
        tun = null
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (e: Exception) { }
    }

    override fun onRevoke() {
        SiteBlockPrefs(this).enabled = false
        stopBlocking()
        stopSelf()
    }

    override fun onDestroy() {
        stopBlocking()
        pool.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.siteblock.STOP"
        private const val CHANNEL = "site_block"
        private const val NOTIF_ID = 4107
        private const val VPN_ADDR = "10.111.222.1"
        private const val FAKE_DNS = "10.111.222.2"

        fun start(context: Context) {
            val i = Intent(context, SiteBlockVpnService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, SiteBlockVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
