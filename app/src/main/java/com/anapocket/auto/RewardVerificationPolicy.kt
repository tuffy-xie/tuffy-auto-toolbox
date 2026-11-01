package com.anapocket.auto
object RewardVerificationPolicy {
 fun stage(completed:Boolean,amount:String?,confirmed:Boolean)=when{confirmed->"REWARD_CONFIRMED";(amount?.toLongOrNull()?:0)>0->"REWARD_DISPLAYED";completed->"TASK_COMPLETED";else->"UNCONFIRMED"}
 fun credit(confirmed:Boolean,deferred:Boolean)=if(confirmed)"REWARD_CONFIRMED" else if(deferred)"CREDIT_PENDING" else "CREDIT_UNCONFIRMED"
}
