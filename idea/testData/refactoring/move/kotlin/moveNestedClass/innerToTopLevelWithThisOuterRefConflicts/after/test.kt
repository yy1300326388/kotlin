package test

class A() {
    fun X.bar() {}
}

class X {
    fun Int.foo() {}

    inner class Y {
        fun test() {
            1.foo()
            with(1) { foo() }
            with(A()) { bar() }
        }
    }
}