package checkcastToObjectArray

fun main(args: Array<String>) {
    val a: Array<BaseInterface> = arrayOf(DerivedClass())
    //Breakpoint!
    foo(a)
}

fun foo(a: Array<out Any>) = 1

interface BaseInterface
class DerivedClass : BaseInterface

// EXPRESSION: foo(a)
// RESULT: 1: I