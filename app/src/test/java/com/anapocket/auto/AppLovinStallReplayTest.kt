package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

class AppLovinStallReplayTest {
    private val activity="com.applovin.adview.AppLovinFullscreenActivity"
    private data class Node(val id:String,val visible:Boolean,val clickable:Boolean,val text:String,val bounds:String)
    private fun replay(name:String)=javaClass.getResourceAsStream("/applovin/$name")!!.bufferedReader().useLines{lines->lines.filter{it.isNotBlank()}.map{val c=it.split('\t');require(c.size==5);Node(c[0],c[1].toBooleanStrict(),c[2].toBooleanStrict(),c[3],c[4])}.toList()}
    private fun ids(nodes:List<Node>)=nodes.filter{it.visible}.mapNotNull{it.id.takeIf{v->v.isNotBlank()}}.toSet()
    @Test fun realImageEndcardIsReviewedWithoutBecomingAPlayableOrATapTarget(){
        val nodes=replay("car5-root-image.tsv");val ids=ids(nodes)
        assertTrue(OpaqueRecoveryPolicy.stallSurface(activity,ids))
        assertFalse(setOf("playable","application-canvas").all{it in ids})
        assertTrue(nodes.any{it.text=="Install"})
        val wrapper=nodes.single{it.clickable&&it.bounds=="0 49 720 1650"}
        assertTrue(ControlRevealTarget.blocks(wrapper.clickable,true))
    }
    @Test fun realPlayableKeepsObservationButItsUnnamedClickthroughBlocksReveal(){
        val nodes=replay("car20-playable.tsv")
        assertTrue(OpaqueRecoveryPolicy.stallSurface(activity,ids(nodes)))
        val wrapper=nodes.single{it.clickable&&it.bounds=="0 49 720 1650"}
        assertTrue(ControlRevealTarget.blocks(wrapper.clickable,true))
        assertFalse(nodes.any{it.visible&&AdSignals.closeLabel(it.text)})
    }
    @Test fun imageShapeInGoogleOrNativePagesDoesNotGrantStallEligibility(){
        val ids=ids(replay("car5-root-image.tsv"))
        assertFalse(OpaqueRecoveryPolicy.stallSurface("com.google.android.gms.ads.AdActivity",ids))
        assertFalse(OpaqueRecoveryPolicy.stallSurface("us.ana_pocket.ANAPocket.views.home.HomeActivity",ids))
        assertFalse(OpaqueRecoveryPolicy.stallSurface(activity,setOf("root")))
    }
}
