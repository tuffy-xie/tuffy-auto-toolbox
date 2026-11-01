package com.anapocket.auto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Replays the observed Skip -> Close transition; these frames are controlled, never real rewards. */
class GoogleAdvanceBudgetRegressionTest {
    @Test fun threeSkipAttemptsLeaveFinalCloseAvailable()=runBlocking {
        val p=object:AdInteractionPort {
            var time=0L;var skip=0;var close=0;var dismissed=false
            override fun now()=time
            override fun stopped()=false
            override suspend fun pause(ms:Long){time+=ms}
            override suspend fun state(state:AdState){}
            override suspend fun frame()=when {
                skip<3->AdFrame(true,false,adSurface=true,closeAvailable=true,advanceControl=true)
                close==0->AdFrame(true,false,adSurface=true,closeAvailable=true)
                !dismissed->AdFrame(true,false,receipt=true)
                else->AdFrame(true,true,watched=1)
            }
            override suspend fun close():Boolean{if(skip<3)skip++ else close++;return true}
            override suspend fun dismissReceipt(){dismissed=true}
        }
        val r=AdInteraction(p).awaitReceipt(0)
        assertTrue(r.completed&&r.reward);assertEquals(3,p.skip);assertEquals(1,p.close)
    }
    @Test fun alternatingControlsNeverRefreshTheirBudgets()=runBlocking {
        val p=object:AdInteractionPort {
            var time=0L;var skip=0;var close=0;var phase=false
            override fun now()=time
            override fun stopped()=false
            override suspend fun pause(ms:Long){time+=ms}
            override suspend fun state(state:AdState){}
            override suspend fun frame():AdFrame{phase=(time/2400)%2==0L;return AdFrame(true,false,adSurface=true,closeAvailable=true,advanceControl=phase)}
            override suspend fun close():Boolean{if(phase)skip++ else close++;return true}
            override suspend fun dismissReceipt(){fail("no real receipt supplied")}
        }
        val r=AdInteraction(p).awaitReceipt(0)
        assertEquals(3,p.skip);assertEquals(3,p.close);assertFalse(r.completed||r.reward)
        assertEquals("ad_timeout_95s",r.reason)
    }
    @Test fun startupAlsoPreservesCloseBudgetAfterSkip()=runBlocking {
        val p=object:StartupAdPort {
            var time=0L;var skip=0;var close=0
            override fun now()=time
            override fun stopped()=false
            override fun sameDate()=true
            override suspend fun pause(ms:Long){time+=ms}
            override suspend fun observedAd(){}
            override suspend fun frame()=when {
                skip<3->StartupAdFrame(ad=true,close=true,advanceControl=true)
                close==0->StartupAdFrame(ad=true,close=true)
                else->StartupAdFrame(normal=true)
            }
            override suspend fun close(){if(skip<3)skip++ else close++}
        }
        assertEquals("DISMISSED",StartupAdSequence(p).run(false).state)
        assertEquals(3,p.skip);assertEquals(1,p.close)
    }
    @Test fun advanceBudgetOnlyRecognizesObservedGoogleSkip() {
        val pkg=StartupPolicy.ANA;val activity="com.google.android.gms.ads.AdActivity"
        val ids=setOf("app-interstitial-slot")
        assertTrue(GoogleDismissPolicy.advanceControl(pkg,activity,ids,"android.widget.Button",true,"Skip"))
        assertFalse(GoogleDismissPolicy.advanceControl(pkg,activity,ids,"android.widget.Button",true,"Close"))
        assertFalse(GoogleDismissPolicy.advanceControl(pkg,activity,emptySet(),"android.widget.Button",true,"Skip"))
        assertFalse(GoogleDismissPolicy.advanceControl("other.app",activity,ids,"android.widget.Button",true,"Skip"))
    }
}
