fun foo() {
    var v: String? = "xyz"
    // It is possible to provide smart cast here
    <!DEBUG_INFO_SMARTCAST!>v<!>.length()
    v = null
    v<!UNSAFE_CALL!>.<!>length()
}