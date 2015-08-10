class My {
    val x: Int

    constructor() {
        val y = bar(<!DANGEROUS_THIS_IN_CONSTRUCTOR!>this<!>)
        val z = <!DANGEROUS_METHOD_CALL_IN_CONSTRUCTOR!>foo()<!>
        x = y + z
    }

    fun foo() = x
}

fun bar(arg: My): Int = arg.x
