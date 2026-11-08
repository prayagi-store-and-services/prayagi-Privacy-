package com.example

import com.example.siteblock.UdpOwnerMap
import org.junit.Assert.*
import org.junit.Test

class UdpOwnerMapTest {
    private val sample = """
  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode ref pointer drops
   1: 0101ACA8:0035 00000000:0000 07 00000000:00000000 00:00000000 00000000  1000   0 12345 2 0000000000000000 0
   2: 0168DE0A:C001 0268DE0A:0035 01 00000000:00000000 00:00000000 00000000 10123   0 12346 2 0000000000000000 0
   3: malformed line
   4: 0168DE0A:ZZZZ 00000000:0000 07 00000000:00000000 00:00000000 00000000  1000   0 1 1 0000000000000000 0
    """.trimIndent()

    @Test fun parsesPortToUid() {
        val m = UdpOwnerMap.parse(sample)
        assertEquals(1000, m[0x0035])
        assertEquals(10123, m[0xC001])
        assertEquals(2, m.size)
    }

    @Test fun emptyAndGarbage() {
        assertTrue(UdpOwnerMap.parse("").isEmpty())
        assertTrue(UdpOwnerMap.parse("sl  local_address rem_address st").isEmpty())
    }
}
