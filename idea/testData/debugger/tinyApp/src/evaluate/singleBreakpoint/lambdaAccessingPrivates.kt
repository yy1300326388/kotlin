package lambdaAccessingPrivates

fun main(args: Array<String>) {
    C().bar()
}

class C {
    private val x = 2

    fun bar() {
        //Breakpoint!
        1
    }
}

fun foo(f: () -> Int): Int {
    return f()
}

// EXPRESSION: foo { x }
// RESULT: 2: I