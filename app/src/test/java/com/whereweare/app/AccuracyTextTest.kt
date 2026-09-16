package com.whereweare.app

import com.whereweare.app.ui.availableAccuracy
import org.junit.Assert.*
import org.junit.Test

class AccuracyTextTest {
    @Test fun missingAndInvalidValuesNeverBecomeNumbers() {
        listOf(null,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-1.0,Double.MAX_VALUE).forEach {assertNull(availableAccuracy(it))}
    }
    @Test fun validSourceValuesIncludeRealZero() {
        assertEquals(0L,availableAccuracy(0.0))
        assertEquals(12L,availableAccuracy(12.8))
    }
}
