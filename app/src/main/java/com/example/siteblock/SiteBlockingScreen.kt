package com.example.siteblock

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SiteBlockingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SiteBlockPrefs(context) }
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var allow by remember { mutableStateOf(prefs.allow) }
    var newSite by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(prefs.lastError) }
    var blockedToday by remember { mutableStateOf(prefs.blockedToday()) }
    var blockedTotal by remember { mutableStateOf(prefs.blockedTotal) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            prefs.enabled = true; enabled = true; prefs.lastError = null; error = null
            SiteBlockVpnService.start(context)
        } else {
            prefs.enabled = false; enabled = false
            error = "Android needs your permission to turn site blocking on."
        }
    }

    fun turnOn() {
        val ask = VpnService.prepare(context)
        if (ask != null) consent.launch(ask) else {
            prefs.enabled = true; enabled = true; prefs.lastError = null; error = null
            SiteBlockVpnService.start(context)
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.testTag("siteblock_back")) { Text("Back") }
        Text("Block adult and gambling sites", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Uses a list of known adult and gambling websites kept on this phone. When an app or browser asks for the " +
                "address of a listed site, ${com.example.Brand.name} answers that it does not exist. Everything stays on the phone.",
            style = MaterialTheme.typography.bodyMedium
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (enabled) "Site blocking is ON" else "Site blocking is OFF", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            if (on) turnOn() else {
                                prefs.enabled = false; enabled = false
                                SiteBlockVpnService.stop(context)
                            }
                        },
                        modifier = Modifier.testTag("siteblock_switch")
                    )
                }
                Text("Blocked today: $blockedToday    Blocked in total: $blockedTotal", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { blockedToday = prefs.blockedToday(); blockedTotal = prefs.blockedTotal; error = prefs.lastError; enabled = prefs.enabled }) { Text("Refresh") }
                if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }
        }
        Text("How site blocking works", style = MaterialTheme.typography.titleMedium)
        Text(
            "1. Turn the switch on. Android asks once to allow a VPN connection. Allow it.\n" +
                "2. ${com.example.Brand.name} makes a VPN that stays inside your phone. Nothing is sent to a ${com.example.Brand.name} server.\n" +
                "3. Every time an app or browser asks for a website address, the phone checks the name against the block list. " +
                "If the name (or its main domain) is on the list, the phone answers 'no such site' and the page does not open.\n" +
                "4. Names that are not on the list are passed on to the DNS server your phone already uses.\n" +
                "5. A key icon shows in the status bar while it is on. Turn the switch off any time.\n" +
                "Only one VPN can run at a time on Android. This has been tested in automated tests and an emulator, not yet on every phone.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("siteblock_how_it_works")
        )
        Text("Sites you allow", style = MaterialTheme.typography.titleMedium)
        Text("If a site was blocked by mistake, add it here. An allowed site (and its subpages) is never blocked.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newSite, onValueChange = { newSite = it }, label = { Text("example.com") }, singleLine = true, modifier = Modifier.weight(1f).testTag("siteblock_allow_input"))
            Button(onClick = {
                val n = DomainNames.normalize(newSite)
                if (n != null && n.contains('.')) { allow = allow + n; prefs.allow = allow; newSite = "" ; if (enabled) { SiteBlockVpnService.stop(context); prefs.enabled = true; SiteBlockVpnService.start(context) } }
            }) { Text("Allow") }
        }
        for (a in allow.sorted()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(a)
                OutlinedButton(onClick = { allow = allow - a; prefs.allow = allow; if (enabled) { SiteBlockVpnService.stop(context); prefs.enabled = true; SiteBlockVpnService.start(context) } }) { Text("Remove") }
            }
        }
        Text("What this can and cannot do", style = MaterialTheme.typography.titleMedium)
        Text(
            "It blocks by website name only. It does not read pages or messages. It reduces access, it is not a guarantee: " +
                "a browser set to use its own secure DNS, a Private DNS setting, another VPN app, a network that uses IPv6 DNS only, or a site missing from the list " +
                "can get around it. Only one VPN can be on at a time on Android. You can turn it off here at any time.",
            style = MaterialTheme.typography.bodySmall
        )
        Text("Privacy", style = MaterialTheme.typography.titleMedium)
        Text(
            "No website names are saved or sent anywhere. Only two counters (today and total) are kept on this phone. " +
                "Questions for sites that are not blocked go to the DNS server your phone already uses.",
            style = MaterialTheme.typography.bodySmall
        )
        Text("List updates (optional)", style = MaterialTheme.typography.titleMedium)
        Text(
            "The app ships with a built-in list. The list is data, so it can be updated without a new app version. It only updates when you tap the button or turn on the daily switch below. It contacts github.com over HTTPS, " +
                "downloads one list file from this project's releases, checks its checksum, and only then uses it. GitHub sees your phone's " +
                "internet address, as with any download. Nothing about you or your browsing is sent.",
            style = MaterialTheme.typography.bodySmall
        )
        val last = BlocklistUpdater.lastUpdated(context)
        Text(
            if (last > 0L) "Using an updated list from " + java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(last))
            else "Using the built-in list.",
            style = MaterialTheme.typography.bodySmall
        )
        var auto by remember { mutableStateOf(prefs.autoUpdate) }
        val dayFmt = { ms: Long -> java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ms)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Keep the block list updated (about once a day)", modifier = Modifier.weight(1f))
            Switch(checked = auto, onCheckedChange = { auto = it; AutoListUpdate.setOn(context, it) }, modifier = Modifier.testTag("siteblock_auto_update"))
        }
        Text(
            AutoListUpdate.statusText(auto, BlocklistUpdater.lastUpdated(context), prefs.autoLastTry, prefs.autoLastFailure, dayFmt) +
                " A new list is used the next time blocking starts.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            enabled = !updating,
            modifier = Modifier.testTag("siteblock_update"),
            onClick = {
                updating = true; updateMsg = "Updating..."
                scope.launch {
                    val r = withContext(Dispatchers.IO) { BlocklistUpdater.update(context) }
                    updating = false
                    updateMsg = when (r) {
                        is BlocklistUpdater.Result.Ok -> "Updated: " + r.names + " names." + (if (enabled) " Blocking was restarted." else "")
                        is BlocklistUpdater.Result.Failed -> r.reason
                    }
                    if (r is BlocklistUpdater.Result.Ok && enabled) {
                        SiteBlockVpnService.stop(context); prefs.enabled = true; SiteBlockVpnService.start(context)
                    }
                }
            }
        ) { Text("Update lists now") }
        updateMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text("Lists and credits", style = MaterialTheme.typography.titleMedium)
        Text(
            "Block list built from: BlockList Project (github.com/blocklistproject/Lists, MIT licence, adult and gambling lists) " +
                "and the UT1 blacklists of Universite Toulouse Capitole (dsi.ut-capitole.fr/blacklists, Creative Commons BY-SA 4.0, " +
                "adult and gambling categories). Lists delivered by a list update also include the BlockList Project phishing, scam and ransomware lists (MIT licence); they are not in the built-in copy. The lists were merged, reduced to domain names and stored in a compact form. " +
                "The data file stays under CC BY-SA 4.0 for the UT1 part. Full text: docs/THIRD_PARTY_LISTS.md in the ${com.example.Brand.name} repository.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
