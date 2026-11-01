package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class H12ParticipatingDayTest {
 @Test fun yesterdayStillProcessingIsNotTodaysTodo(){assertFalse(ParticipatingDayPolicy.current("2026/10/31まで","2026-11-01"));assertTrue(ParticipatingDayPolicy.current("2026/11/1まで","2026-11-01"))}
 @Test fun laterAndUnknownTaskDatesCannotBeAdopted(){assertFalse(ParticipatingDayPolicy.current("2026/11/30まで","2026-11-01"));assertFalse(ParticipatingDayPolicy.current(null,"2026-11-01"))}
}
