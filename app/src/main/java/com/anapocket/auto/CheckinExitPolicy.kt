package com.anapocket.auto

/** An unfinished reserved game or human verification is not a completed foreground task. */
object CheckinExitPolicy {
    fun preserveScene(ownAppForeground:Boolean,probe:Boolean,hold:Boolean,reservedUnfinished:Boolean)=
        ownAppForeground&&!probe&&(hold||reservedUnfinished)
}
