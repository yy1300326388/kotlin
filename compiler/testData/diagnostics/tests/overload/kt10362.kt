val Any.extValWithInvoke : (String) -> String
    get() = { "" }

val String.extValWithInvoke : (Any) -> Int
    get() = { 0 }

fun test(): Int {
    val x = "".extValWithInvoke("")
    return x
}