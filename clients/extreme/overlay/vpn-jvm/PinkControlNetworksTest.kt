package com.pinkiptv.extreme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinkControlNetworksTest {
    @Test fun dnsSuccessWithoutHttpsCannotSelectAnUnusableValidatedUnderlay() {
        val dns = setOf("cellular", "wifi")
        val probes = mutableListOf<String>()
        val selected = selectPinkControlNetwork(listOf("cellular", "wifi"), "vpn", { it == "cellular" }) {
            probes.add(it)
            it in dns && it == "wifi"
        }
        assertEquals("wifi", selected)
        assertEquals(listOf("cellular", "wifi"), probes)
    }

    @Test fun validatedDoesNotGuaranteeControlReachability() {
        val probes = mutableListOf<String>()
        val selected = selectPinkControlNetwork(listOf("cellular", "wifi"), null, { true }) {
            probes.add(it)
            it == "wifi"
        }
        assertEquals("wifi", selected)
        assertEquals(listOf("cellular", "wifi"), probes)
    }

    @Test fun usablePhysicalDefaultWinsRegardlessOfEnumerationOrder() {
        val probes = mutableListOf<String>()
        assertEquals("wifi", selectPinkControlNetwork(listOf("cellular", "wifi"), "wifi", { true }) {
            probes.add(it)
            true
        })
        assertEquals(listOf("wifi"), probes)
    }

    @Test fun preferenceCannotIntroduceANetworkOutsideThePhysicalCandidates() {
        assertEquals("wifi", selectPinkControlNetwork(listOf("wifi"), "vpn", { true }) { true })
    }

    @Test fun unreachableCandidatesFailClosedAndEachIsProbedOnlyOnce() {
        val probes = mutableListOf<String>()
        assertNull(selectPinkControlNetwork(listOf("wifi", "wifi", "cellular"), null, { true }) {
            probes.add(it)
            false
        })
        assertEquals(listOf("wifi", "cellular"), probes)
        assertNull(selectPinkControlNetwork(emptyList<String>(), "vpn", { true }) { true })
    }
}
