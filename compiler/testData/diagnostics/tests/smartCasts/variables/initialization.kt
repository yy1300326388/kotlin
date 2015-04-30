fun foo() {
    var v: Any = "xyz"
    // It is possible to provide smart cast here
    // ?: should we do it in practice?
    <!DEBUG_INFO_SMARTCAST!>v<!>.length()
    v = 42
    v.<!UNRESOLVED_REFERENCE!>length<!>()
}