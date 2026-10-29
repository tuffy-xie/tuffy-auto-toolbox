package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class RemoteCategoryTest {
 @Test fun nativeShinkansenAndFlightDailyTitlesParse(){assertEquals(Cat.SHINKANSEN,Cards.fromTitle("新幹線で100km移動しよう！")!!.cat);assertEquals(200000,Cards.fromTitle("飛行機で200km移動しよう！")!!.meters);assertEquals(1000000,Cards.fromTitle("飛行機で1,000km移動しよう！")!!.meters);assertEquals(1000000,Dist.parse("飛行機で1,000km移動しよう！")!!.meters)}
 @Test fun mixedWeeklyAndOverLimitRemainExcluded(){assertNull(Cards.fromTitle("新幹線や飛行機で100km移動"));assertNull(Cards.fromTitle("毎週 新幹線で100km移動"));assertTrue(Cards.select(listOf(Cards.fromTitle("飛行機で1,000km移動しよう！")!!),Cat.FLIGHT,400000).matched.isEmpty())}
 @Test fun legacySettingsKeepFourAndNewModesOffAndUnset(){val s=AppSettings.fromJson("{}");for(c in listOf(Cat.WALK,Cat.BIKE,Cat.CAR,Cat.MOV_ANY))assertTrue(s.catOn(c));for(c in listOf(Cat.SHINKANSEN,Cat.FLIGHT)){assertFalse(s.catOn(c));assertEquals(0,s.kmOf(c))}}
 @Test fun realTravelDistancesCanExceedOld200kmLimit(){assertEquals(1000,CategoryDistancePolicy.parse(Cat.FLIGHT,"1000"));assertEquals(300,CategoryDistancePolicy.parse(Cat.SHINKANSEN,"300"));assertNull(CategoryDistancePolicy.parse(Cat.WALK,"201"));assertNull(CategoryDistancePolicy.parse(Cat.FLIGHT,""));assertNull(CategoryDistancePolicy.parse(Cat.FLIGHT,"0"))}
 @Test fun coverageUsesConfiguredLongDistanceAndCannotPretendLessCoversMore(){assertTrue(CategoryCompletionPolicy.covers(1000,1000));assertFalse(CategoryCompletionPolicy.covers(400,1000))}
}
