class My {
    val x: Int

    init {
        x = <!DANGEROUS_METHOD_CALL_IN_CONSTRUCTOR!>foo()<!>
    }

    fun foo(): Int = x
}