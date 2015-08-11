open class Base {
    init {
        register(<!DANGEROUS_THIS_IN_OPEN_CLASS_CONSTRUCTOR!>this<!>)
        <!DANGEROUS_METHOD_CALL_IN_OPEN_CLASS_CONSTRUCTOR!>foo()<!>
    }

    open fun foo() {}
}

fun register(arg: Base) {
    arg.foo()
}
