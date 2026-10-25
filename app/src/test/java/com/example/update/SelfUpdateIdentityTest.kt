package com.example.update
import org.junit.Test
import org.junit.Assert.*
class SelfUpdateIdentityTest {
    private fun ok(pkg:String?="self",installed:Long=46,archive:Long=47,expected:Long=47,name:String?="1.1.38",current:Set<String> = setOf("signer"),signers:Set<String> = setOf("signer")) =
        SelfUpdateIdentity.matches("self",pkg,installed,archive,expected,"1.1.38",name,current,signers)
    @Test fun exactNewerSelfUpdateAccepted() { assertTrue(ok()) }
    @Test fun otherOrUnreadablePackageRefused() { assertFalse(ok(pkg="other"));assertFalse(ok(pkg=null)) }
    @Test fun sameOrOlderRefused() {assertFalse(ok(archive=46,expected=46));assertFalse(ok(archive=45,expected=45))}
    @Test fun wrongCodeOrNameRefused() {assertFalse(ok(archive=48));assertFalse(ok(name="wrong"));assertFalse(ok(name=null))}
    @Test fun emptyChangedOrExtraSignerRefused() {assertFalse(ok(current=emptySet()));assertFalse(ok(signers=emptySet()));assertFalse(ok(signers=setOf("wrong")));assertFalse(ok(signers=setOf("signer","extra")))}
}
