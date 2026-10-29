package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class DistanceLimitPolicyTest {
    @Test fun actualTenAndFortyKilometreSettingsArePreserved() {
        assertEquals(10,DistanceLimitPolicy.parse("10")); assertEquals(40,DistanceLimitPolicy.parse("40"))
        assertEquals(5,DistanceLimitPolicy.parse("5")); assertEquals(200,DistanceLimitPolicy.parse("200"))
    }
    @Test fun InvalidInputCannotSilentlyRunWithADefaultOrChangedLimit() {
        for (s in listOf("", "abc", "0", "-1", "201", "10.5")) assertNull(DistanceLimitPolicy.parse(s))
    }
}
