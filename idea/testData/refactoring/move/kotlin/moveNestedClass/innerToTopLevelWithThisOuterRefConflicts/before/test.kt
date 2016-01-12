package test

class A() {
    fun X.bar() {}
}

class X {
    fun Int.foo() {}

    inner class <caret>Y {
        fun test() {
            1.foo()
            with(1) { foo() }
            with(A()) { bar() }
        }
    }
}