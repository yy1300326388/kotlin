// !DIAGNOSTICS: -UNUSED_PARAMETER

object Right
object Wrong

fun overloadedFun5(vararg ss: String) = Right
fun overloadedFun5(s: String, vararg ss: String) = Wrong

val test5: Right = <!OVERLOAD_RESOLUTION_AMBIGUITY!>overloadedFun5<!>("")
val test5a = <!OVERLOAD_RESOLUTION_AMBIGUITY!>overloadedFun5<!>("", "")

fun overloadedFun6(s1: String) = Right
fun overloadedFun6(s1: String, s2: String) = Wrong
fun overloadedFun6(s1: String, s2: String, s3: String) = Wrong
fun overloadedFun6(s: String, vararg ss: String) = Wrong

val test6: Right = overloadedFun6("")
