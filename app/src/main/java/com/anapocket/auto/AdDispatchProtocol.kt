package com.anapocket.auto

/** Shared irreversible-action fence; failure before a durable write cannot reach the gesture. */
object AdDispatchProtocol {
    suspend fun dispatch(persist:suspend(String)->Unit,gesture:suspend()->Boolean):Boolean {
        persist("ARMED")
        val dispatched=gesture()
        if(dispatched)persist("DISPATCHED")
        return dispatched
    }
}
