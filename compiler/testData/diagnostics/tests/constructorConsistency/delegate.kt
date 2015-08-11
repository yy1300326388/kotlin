class Delegate(val x: Int) {
    fun get(t: Any?, p: PropertyMetadata): Int = x
}

class My {
    val x: Int by Delegate(<!DANGEROUS_THIS_IN_CONSTRUCTOR!>this<!>.foo())

    fun foo(): Int = x
}
