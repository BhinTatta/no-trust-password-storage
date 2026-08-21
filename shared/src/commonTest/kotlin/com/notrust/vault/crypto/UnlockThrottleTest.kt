package com.notrust.vault.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UnlockThrottleTest {

    @Test
    fun firstFewFailuresHaveNoDelay() {
        assertEquals(0L, UnlockThrottle.delayMillisAfter(0))
        assertEquals(0L, UnlockThrottle.delayMillisAfter(1))
        assertEquals(0L, UnlockThrottle.delayMillisAfter(2))
    }

    @Test
    fun delayGrowsExponentiallyOnceFreeAttemptsAreUsedUp() {
        assertEquals(2_000L, UnlockThrottle.delayMillisAfter(3))
        assertEquals(4_000L, UnlockThrottle.delayMillisAfter(4))
        assertEquals(8_000L, UnlockThrottle.delayMillisAfter(5))
        assertEquals(16_000L, UnlockThrottle.delayMillisAfter(6))
    }

    @Test
    fun delayIsMonotonicallyNonDecreasing() {
        var previous = 0L
        for (failures in 0..30) {
            val delay = UnlockThrottle.delayMillisAfter(failures)
            assertTrue(delay >= previous, "delay decreased at $failures failures: $previous -> $delay")
            previous = delay
        }
    }

    @Test
    fun delayIsCappedAtFiveMinutes() {
        val cap = 5 * 60 * 1000L
        assertEquals(cap, UnlockThrottle.delayMillisAfter(50))
        assertEquals(cap, UnlockThrottle.delayMillisAfter(10_000))
    }

    @Test
    fun neverThrowsForLargeInputs() {
        // A huge failure count must degrade to the cap, not overflow or crash.
        val delay = UnlockThrottle.delayMillisAfter(Int.MAX_VALUE)
        assertEquals(5 * 60 * 1000L, delay)
    }

    @Test
    fun rejectsNegativeInput() {
        assertFailsWith<IllegalArgumentException> { UnlockThrottle.delayMillisAfter(-1) }
    }
}
