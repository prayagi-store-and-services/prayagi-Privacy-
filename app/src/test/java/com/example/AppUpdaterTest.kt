package com.example
import com.example.update.AppUpdater
import org.junit.Test
import org.junit.Assert.*
class AppUpdaterTest {
 @Test fun onlyEcoIsTheUpdateDestination() = assertEquals("com.prayagi.netraeco",AppUpdater.ECO_PACKAGE)
 @Test fun downloaderAndInstallerEntryPointsAreAbsent() {
  val names=AppUpdater::class.java.declaredMethods.map { it.name }
  listOf("download","install","savedFile","checkNow","checkIfDue","parse").forEach { assertFalse(it,names.contains(it)) }
 }
}
