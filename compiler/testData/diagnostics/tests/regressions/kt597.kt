//KT-597 Type inference failed

fun <T> Array<T>?.get(i: Int) : T {
    if (this != null)
        return <!DEBUG_INFO_SMARTCAST!>this<!>.get(i) // <- inferred type is Any? but &T was excepted
    else throw NullPointerException()
}

fun Int?.inc() : Int {
    if (this != null)
        return <!DEBUG_INFO_SMARTCAST!>this<!>.inc()
    else
        throw NullPointerException()
}

fun test(arg: Int?) {
   var i : Int? = arg
   var <!UNUSED_VARIABLE!>i_inc<!> = <!UNUSED_CHANGED_VALUE!>i++<!> // <- expected Int?, but returns Any?
}
