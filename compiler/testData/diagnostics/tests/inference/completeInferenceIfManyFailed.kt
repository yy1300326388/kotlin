// !CHECK_TYPE

package d

fun <T: Any> joinT(<!UNUSED_PARAMETER!>x<!>: Int, vararg <!UNUSED_PARAMETER!>a<!>: T): T? {
    return null
}

fun <T: Any> joinT(<!UNUSED_PARAMETER!>x<!>: Comparable<*>, <!UNUSED_PARAMETER!>y<!>: T): T? {
    return null
}

fun test() {
    val x2 = <!NONE_APPLICABLE!>joinT<!>(Unit, "2")
    checkSubtype<String?>(<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>x2<!>)
}