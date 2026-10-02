package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log

import com.example.data.model.AudioLockMode

class AudioLockManager(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var isRecordingLocked = false
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    @Synchronized
    fun acquireMicrophoneLock(mode: AudioLockMode = AudioLockMode.EXCLUSIVE_LINE_RESERVATION): Boolean {
        if (isRecordingLocked) return true

        try {
            // Step 1: Request exclusive Audio Focus
            requestAudioFocus()

            if (mode == AudioLockMode.AUDIO_FOCUS_ONLY) {
                isRecordingLocked = true
                Log.d("AudioLockManager", "Audio focus guard lock engaged (Power Saver mode)")
                return true
            }

            // Step 2: Acquire AudioRecord hardware line if RECORD_AUDIO permission is present
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            if (bufferSize > 0) {
                @SuppressLint("MissingPermission")
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    record.startRecording()
                    audioRecord = record
                    isRecordingLocked = true
                    Log.d("AudioLockManager", "Microphone exclusive guard lock engaged successfully")
                    return true
                } else {
                    record.release()
                }
            }
        } catch (e: SecurityException) {
            Log.w("AudioLockManager", "RECORD_AUDIO permission not granted yet: ${e.message}")
        } catch (e: Exception) {
            Log.e("AudioLockManager", "Failed to engage audio lock: ${e.message}", e)
        }

        // Even if AudioRecord was not initialized, Audio Focus was requested
        isRecordingLocked = true
        return true
    }

    @Synchronized
    fun releaseMicrophoneLock() {
        try {
            audioRecord?.let {
                if (it.state == AudioRecord.STATE_INITIALIZED) {
                    try {
                        it.stop()
                    } catch (ignored: Exception) {}
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e("AudioLockManager", "Error releasing AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
            isRecordingLocked = false
            abandonAudioFocus()
            Log.d("AudioLockManager", "Microphone exclusive guard lock released")
        }
    }

    val isLocked: Boolean
        get() = isRecordingLocked

    private fun requestAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { /* handle if necessary */ }
                    .build()
                focusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        } catch (e: Exception) {
            Log.e("AudioLockManager", "Error requesting audio focus: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.e("AudioLockManager", "Error abandoning audio focus: ${e.message}")
        }
    }
}
