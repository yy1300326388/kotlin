package multiLineVariableKt6458

fun main(args: Array<String>) {
    //Breakpoint!
    val ab = 1
    val a =
            foo()
}

fun foo() {}

// STEP_OVER: 2