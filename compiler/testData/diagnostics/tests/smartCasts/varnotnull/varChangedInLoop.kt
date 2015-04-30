public fun foo() {
    var i: Int? = 1
    while (i != 10) {
        <!UNUSED_CHANGED_VALUE!>i<!UNSAFE_CALL!>++<!><!>      // Here smart cast should not be performed due to a successor
        i = null
    }
}