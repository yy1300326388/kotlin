package bar

class S<T> {
    fun foo() {
        <!UNRESOLVED_REFERENCE!>T<!>
        <!TYPE_PARAMETER_ON_LHS_OF_DOT!>T<!>.<!UNRESOLVED_REFERENCE!>create<!>()
    }
}