package loadLambda

fun main(args: Array<String>) {
    javaClass<C>().getDeclaredFields().forEach {
        println(it)
        it.setAccessible(true)
    }
    C().bar()
}

class C {
    private val x: Int = 2

    fun bar() {
        //Breakpoint!
        1
    }

    fun x() = 2
}

fun foo(f: () -> Int): Int {
    return f()
}

// EXPRESSION: foo { C().x }
// RESULT: 2: I

// EXPRESSION: x
// RESULT: 2: I