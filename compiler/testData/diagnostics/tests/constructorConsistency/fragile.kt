class My {
    val x: Int

    val w = Wrapper(bar(@fragile this))

    constructor() {
        val y = bar(@fragile this)
        val z = @fragile foo()
        x = y + z
    }

    fun foo() = x
}

data class Wrapper(val x: Int)

fun bar(arg: My): Int = arg.x
