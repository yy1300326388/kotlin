public fun foo() {
    var i: Int? = 1
    while (i != 10) {
        <!DEBUG_INFO_SMARTCAST!>i<!>++
    }
}