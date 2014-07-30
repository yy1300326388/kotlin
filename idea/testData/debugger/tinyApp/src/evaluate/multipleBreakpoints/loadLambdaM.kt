package loadLambdaM

fun main(args: Array<String>) {
    C().bar()
}

class C {
    public val x: Int = 2

    fun bar() {
        // EXPRESSION: foo { C().x() }
        // RESULT: 2: I

        // EXPRESSION: x
        // RESULT: 2: I

        //Breakpoint!
        1
        // EXPRESSION: foo { C().x() } + 1
        // RESULT: 3: I

        // EXPRESSION: x + 1
        // RESULT: 3: I

        //Breakpoint!
        1
    }

    fun x() = 2
}

fun foo(f: () -> Int): Int {
    return f()
}

