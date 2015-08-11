class My(x: Int) {
    val y: Int = <!DANGEROUS_METHOD_CALL_IN_CONSTRUCTOR!>foo(x)<!>

    fun foo(x: Int): Int = x + y
}