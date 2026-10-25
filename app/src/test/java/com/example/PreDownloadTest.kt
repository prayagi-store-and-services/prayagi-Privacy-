package com.example
import com.example.update.PreDownload
import org.junit.Test
import org.junit.Assert.*
class PreDownloadTest {
 @Test fun everyNetworkBatteryCombinationRefusesApkPrefetch() {
  for(bits in 0..63) for(size in listOf(-1L,0L,1L,1000000000L)) {
   fun b(i:Int)=bits and (1 shl i)!=0
   assertFalse(PreDownload.shouldPreDownload(b(0),b(1),b(2),b(3),b(4),b(5),size))
  }
 }
 @Test fun oldUpdaterApksAreNeverRetained() { assertFalse(PreDownload.keepFile("app-v9.9.9.apk","1.0.0",0)) }
}
