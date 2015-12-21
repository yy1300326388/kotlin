package testing

interface I {
    fun <caret>f() {

    }
}

class A(i: I) : I by I

class B(i: I) : I by I {
    override fun f() {
    }
}

class C(i: I) : I by I : I

// REF: (in testing.B).f()

