package testing

interface I {
    val <caret>p: Int
        get() = 0
}

class A(i: I) : I by I

class B(i: I) : I by I {
    override val p = 5
}

class C : I


// REF: (in testing.B).p


