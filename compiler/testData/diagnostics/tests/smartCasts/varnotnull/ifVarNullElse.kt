public fun fooNotNull(s: String) {
    System.out.println("Length of $s is ${s.length()}")
}

public fun foo(arg: String?) {
    var s: String? = arg
    if (s == null) {
        // Coming soon
    } else {
        fooNotNull(<!DEBUG_INFO_SMARTCAST!>s<!>)
    }
}
