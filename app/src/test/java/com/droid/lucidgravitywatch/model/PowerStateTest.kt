package com.droid.lucidgravitywatch.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PowerStateTest {

    @Test
    fun onlyDocumentedSleepStatesAreAsleep_andUnknownIsNeither() {
        val asleep = PowerState.entries.filter { it.isSleeping }.toSet()
        assertEquals(setOf(PowerState.SLEEP, PowerState.SLEEP_CHARGE, PowerState.SLEEP_UPDATE), asleep)

        val notAwake = PowerState.entries.filterNot { it.isAwake }.toSet()
        assertEquals(asleep + PowerState.UNKNOWN, notAwake)

        // 12 and 13 are undocumented; a Gravity reports 13 while online, so neither blocks controls
        assertEquals(PowerState.UNDOCUMENTED_13, PowerState.fromProto(13))
        assertEquals(true, PowerState.UNDOCUMENTED_12.isAwake)
        assertEquals(true, PowerState.UNDOCUMENTED_13.isAwake)
        assertEquals(PowerState.UNKNOWN, PowerState.fromProto(42))
    }
}
