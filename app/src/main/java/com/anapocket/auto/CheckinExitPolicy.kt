package com.anapocket.auto

/** An unfinished reserved game or human verification is not a completed foreground task. */
object CheckinExitPolicy {
    fun preserveScene(ownAppForeground:Boolean,probe:Boolean,hold:Boolean,reservedUnfinished:Boolean)=
        ownAppForeground&&!probe&&(hold||reservedUnfinished)
    /** A kept scene is still locked; only another check-in that is already due (and starts right away) skips the lock. */
    fun lockAfter(owns:Boolean,anotherCheckinDue:Boolean)=owns&&!anotherCheckinDue
}
