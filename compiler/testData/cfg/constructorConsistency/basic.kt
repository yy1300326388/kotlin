class My {
    val x: Int

    constructor() {
        val y = bar(this)
        val z = foo()
        x = y + z
    }

    fun foo() = x
}

fun bar(arg: My): Int = arg.x
