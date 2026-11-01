package com.anapocket.auto
import org.junit.Test
import org.junit.Assert.*
class SelectionAndDispatchTest {
 @Test fun sixCategoriesCanToggleWithoutChangingDistances(){for(c in Cat.values()){val a=AppSettings();val b=a.copy(catEnabled=a.catEnabled+(c.name to !a.catOn(c)));assertEquals(!a.catOn(c),b.catOn(c));assertEquals(a.catKm,b.catKm);assertEquals(a.hour,b.hour);assertEquals(a.adEnabled,b.adEnabled)}}
 @Test fun partialQueueContainsOnlySelectedWithinConfiguredRange(){val s=AppSettings(catEnabled=Cat.values().associate{it.name to (it==Cat.WALK||it==Cat.CAR)});assertEquals(listOf(Cat.WALK,Cat.CAR),TravelSelectionPolicy.queue(Cat.values().toList(),s))}
 @Test fun allOffLeavesAdsAndLoginSequenceAvailable(){val s=AppSettings().copy(catEnabled=Cat.values().associate{it.name to false});assertTrue(TravelSelectionPolicy.queue(Cat.values().toList(),s).isEmpty());assertTrue(s.adEnabled);assertEquals(25,s.adTarget);assertTrue(s.enabled)}
 @Test fun unconfiguredLongRangeIsNotSilentlyAssigned(){val s=AppSettings(catEnabled=Cat.values().associate{it.name to true});assertFalse(TravelSelectionPolicy.queue(Cat.values().toList(),s).contains(Cat.FLIGHT));assertEquals(0,s.kmOf(Cat.FLIGHT))}
 @Test fun cancelledCategoryStopsNewWorkButFinishesReservedAd(){assertEquals("DISABLED_BY_USER",TravelSelectionPolicy.decision(false,false));assertEquals("FINISH_EXISTING_ONLY",TravelSelectionPolicy.decision(false,true));assertEquals("ENABLED",TravelSelectionPolicy.decision(true,false))}
 @Test fun staleAlarmsCannotReturnAfterOffOn(){assertFalse(CheckinDispatchPolicy.accepts(false,2,1));assertFalse(CheckinDispatchPolicy.accepts(true,3,1));assertTrue(CheckinDispatchPolicy.accepts(true,3,3))}
 @Test fun legacyEnabledPlanGenerationZeroStillWorks(){assertTrue(CheckinDispatchPolicy.accepts(true,0,0));assertFalse(CheckinDispatchPolicy.accepts(false,0,0))}
 @Test fun oldBusinessDateIsNeverDispatched(){assertFalse(CheckinDispatchPolicy.accepts(true,3,3,false))}
 @Test fun displayedPrizeDoesNotConfirmWalletCredit(){assertEquals("REWARD_DISPLAYED",RewardVerificationPolicy.stage(true,"1",false));assertEquals("CREDIT_PENDING",RewardVerificationPolicy.credit(false,true));assertEquals("CREDIT_UNCONFIRMED",RewardVerificationPolicy.credit(false,false));assertEquals("TASK_COMPLETED",RewardVerificationPolicy.stage(true,"0",false));assertEquals("REWARD_CONFIRMED",RewardVerificationPolicy.stage(true,"1",true))}
 @Test fun actualHyperOsUnlockedMarkerAndLockedCardAreDistinguished(){assertTrue(ManagedCleanupPolicy.card("いきなりステーキ,未加锁",setOf("いきなりステーキ")));assertFalse(ManagedCleanupPolicy.card("いきなりステーキ,已加锁",setOf("いきなりステーキ")));assertFalse(ManagedCleanupPolicy.card("Tuffy的自动百宝箱,未加锁",setOf("いきなりステーキ")))}
 @Test fun onlyExactUnlockedManagedCardCanBeRemoved(){assertTrue(ManagedCleanupPolicy.card("ANA Pocket,未锁定",setOf("ANA Pocket")));assertFalse(ManagedCleanupPolicy.card("Tuffy,未锁定",setOf("ANA Pocket")));assertFalse(ManagedCleanupPolicy.card("ANA Pocket,已锁定",setOf("ANA Pocket")));assertFalse(ManagedCleanupPolicy.card("ANA Pocket Plus,未锁定",setOf("ANA Pocket")))}
}
