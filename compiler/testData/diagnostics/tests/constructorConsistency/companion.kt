class My {

    val x = <!DANGEROUS_METHOD_CALL_IN_CONSTRUCTOR!>foo()<!>

    val w = bar()

    fun foo() = 0

    companion object {
        
        val y = <!INACCESSIBLE_OUTER_CLASS_EXPRESSION!>foo()<!>

        val u = <!DANGEROUS_METHOD_CALL_IN_CONSTRUCTOR!>bar()<!>

        val z: Int? = bar()

        fun bar() = 1
    }
}