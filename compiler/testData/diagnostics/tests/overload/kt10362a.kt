val String.extValWithInvoke : (Any) -> Int
    get() = { 0 }

val Any.extValWithInvoke : (String) -> String
    get() = { "" }

fun test(): Int {
    val x = "".extValWithInvoke("")
    return x
}