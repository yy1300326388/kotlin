package loadLambda

fun main(args: Array<String>) {
    //Breakpoint!
    args
}

fun foo(f: () -> Int): Int {
    return f()
}

// EXPRESSION: foo { val a = 1; a }
// RESULT: 1: I