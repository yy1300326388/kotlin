// Check that evaluate expression works inside inline function
package parametersOfInlineFun

fun main(args: Array<String>) {
    val a = A(1)
    // RESUME: 1
    //Breakpoint!
    a.foo { 1 + 1 }
}

inline fun A.foo(f: (i: Int) -> Unit) {
    val primitive = 1
    val array = arrayOf(1)
    val str = "str"
    val list = listOf("str")
    f(1)
}

class A(val prop: Int)

// EXPRESSION: `primitive$inline_var`
// RESULT: 1: I

// EXPRESSION: it
// RESULT: 1: I

// EXPRESSION: `array$inline_var`
// RESULT: instance of java.lang.Integer[1] (id=ID): [Ljava/lang/Integer;

// EXPRESSION: `str$inline_var`
// RESULT: "str": Ljava/lang/String;

// EXPRESSION: `list$inline_var`
// RESULT: instance of java.util.Collections$SingletonList(id=ID): Ljava/util/Collections$SingletonList;

// EXPRESSION: `$receiver$inline_var`.prop
// RESULT: 1: I