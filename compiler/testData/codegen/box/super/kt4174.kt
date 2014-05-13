open class C(val f: () -> Unit) {
    {
        f()
    }
}

class B(var x: Int) {
    fun foo() {
        class Z : C({x = 3}) {}
        Z()
    }

    fun foo2() {
        class Z : C(object : C ({x += 1}) {
            fun run() = {x *= 2}
        }.run())

        Z()
    }
}


fun box() : String {
    val b = B(1)
    b.foo()
    if (b.x != 3) return "fail: b.x = ${b.x}"

    val b2 = B(10)
    b2.foo2()
    if (b2.x != 22) return "fail: b.x = ${b2.x}"
    return "OK"
}