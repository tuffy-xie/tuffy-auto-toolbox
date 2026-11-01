package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class LockScreenConfirmationTest {
 @Test fun firstOffFrameIsNotStableCompletion(){
  val p=LockScreenConfirmation()
  assertFalse(p.observe(0,true,true));assertFalse(p.observe(2499,true,true));assertTrue(p.observe(2500,true,true))
 }
 @Test fun realNotificationWakeResetsTheConfirmationWindow(){
  val p=LockScreenConfirmation()
  assertFalse(p.observe(0,true,true));assertFalse(p.observe(1570,false,true))
  assertFalse(p.observe(6500,true,true));assertFalse(p.observe(8999,true,true));assertTrue(p.observe(9000,true,true))
 }
 @Test fun screenOffWithoutKeyguardCannotComplete(){
  val p=LockScreenConfirmation()
  assertFalse(p.observe(0,true,false));assertFalse(p.observe(5000,true,false))
  assertFalse(p.observe(6000,true,true));assertFalse(p.observe(8500,false,true));assertFalse(p.observe(12000,true,true))
 }
}
