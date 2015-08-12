fun foo() = 42

class Your {
    fun bar() = 13
}

class My {
    val your = Your()

    val x = foo()

    val y = Your().bar()

    val z = your.bar()

    // This extension function also can use our properties,
    // so the call is also dangerous
    val w = your.<!DANGEROUS_METHOD_CALL_IN_CONSTRUCTOR!>gav()<!>

    val v = Your().<!DANGEROUS_METHOD_CALL_IN_CONSTRUCTOR!>gav()<!>

    val t = your.other()

    val r = Your().other()

    fun Your.gav() = if (your.bar() == 0) 0 else 7
}

fun Your.other() = 3