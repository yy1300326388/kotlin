class Delegate(val x: Int) {
    fun get(t: Any?, p: PropertyMetadata): Int = x
}

class My {
    // x has no backing field, so this is safe here
    val x: Int by Delegate(this.foo())

    fun foo(): Int = x
}
