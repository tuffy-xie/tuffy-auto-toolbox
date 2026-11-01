package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

class GoogleNativeCloseRegressionTest {
    private fun m(a:String="com.google.android.gms.ads.AdActivity",c:String="android.widget.ImageButton",l:String="Interstitial close button",v:Boolean=true,e:Boolean=true,k:Boolean=true)=GoogleNativeClosePolicy.matches(a,c,l,v,e,k)
    @Test fun actualNativeGoogleImageButtonIsRecognized(){assertTrue(m())}
    @Test fun otherSdkGenericTextAndOtherControlsAreRejected(){
        assertFalse(m(a="com.other.AdActivity"));assertFalse(m(c="android.widget.TextView"));assertFalse(m(l="Install"));assertFalse(m(l="Close"))
    }
    @Test fun unavailableNativeButtonsAreNeverAuthorized(){assertFalse(m(v=false));assertFalse(m(e=false));assertFalse(m(k=false))}
}
