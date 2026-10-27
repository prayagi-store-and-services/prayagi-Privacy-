package com.example.update

/** Retired policy. No environment, battery or network state permits per-app APK download. */
object PreDownload {
    fun shouldPreDownload(online:Boolean,unmetered:Boolean,roaming:Boolean,dataSaverOn:Boolean,batteryLow:Boolean,alreadySaved:Boolean,sizeBytes:Long)=false
    fun keepFile(fileName:String,installedVersionName:String,ageMs:Long)=false
}
