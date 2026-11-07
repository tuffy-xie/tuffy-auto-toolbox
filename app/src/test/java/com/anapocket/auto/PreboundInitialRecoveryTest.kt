package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class PreboundInitialRecoveryTest {
    private val a=PendingInitialJoin("2026-11-06","CAR|15km","CHoriginal",1793903722664,1,1,true,false,0,"AD_OPENING","AD_CLOSE_AVAILABLE",false,true,"","DISPATCHED")
    private val steps=listOf(50,50,50,100,100)
    private val labels=listOf("チャレンジをはじめました！","1回目のブーストをしますか？","現在のチャレンジ達成後の報酬：","50pt","+50","+50","+50","+100","+100")
    private fun allowed(value:PendingInitialJoin=a,pending:Int=1,date:String=a.date,id:Boolean=true,panel:Boolean=true,text:List<String> = labels)=
        NativeChallengeReceiptPolicy.boundOfficialInitial(value,pending,date,id,panel,50,steps,text)
    @Test fun actualDispatchedCloseAvailableRecordCanReconcileWithoutAnotherDispatch(){
        assertTrue(allowed());assertTrue(allowed(a.copy(taskState="UNCONFIRMED",adState="UNCONFIRMED",reason="process_or_run_interrupted")))
        assertEquals("DISPATCHED",a.dispatchPhase);assertFalse(a.confirmed)
    }
    @Test fun legacyMissingBindingOrWrongIdentityAndMultiplePendingRemainBlocked(){
        assertFalse(allowed(id=false));assertFalse(allowed(pending=2));assertFalse(allowed(date="2026-11-07"));assertFalse(allowed(panel=false))
    }
    @Test fun reservedArmedUndepartedOrAlreadyConfirmedCannotBecomeReceipt(){
        assertFalse(allowed(a.copy(dispatchPhase="RESERVED")));assertFalse(allowed(a.copy(dispatchPhase="ARMED")))
        assertFalse(allowed(a.copy(departed=false)));assertFalse(allowed(a.copy(confirmed=true,joined=true,taskState="JOINED",adState="REWARD_CONFIRMED")))
    }
    @Test fun boostAttemptWrongTupleOrChangedOfficialOutcomeCannotUseInitialRecovery(){
        assertFalse(allowed(a.copy(initial=false)));assertFalse(allowed(a.copy(index=2,attempts=2)));assertFalse(allowed(a.copy(boost=1)))
        assertFalse(allowed(text=labels.map{if(it=="50pt")"100pt" else it}));assertFalse(allowed(text=labels.drop(1)))
    }
}
