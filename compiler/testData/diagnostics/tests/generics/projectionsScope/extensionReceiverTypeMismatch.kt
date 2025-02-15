class A<T> {
    fun T.foo() {}
    fun Out<T>.bar() {}
}
class Out<out E>

public inline fun <T, R> with(receiver: T, f: T.() -> R): R = receiver.f()

fun test(x: A<out CharSequence>, y: Out<CharSequence>) {
    with(x) {
        // TODO: this diagnostic could be replaced with TYPE_MISMATCH_DUE_TO_TYPE_PROJECTION
        "".<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>foo<!>()
        <!TYPE_MISMATCH_DUE_TO_TYPE_PROJECTIONS!>y<!>.bar()

        with(y) {
            <!TYPE_MISMATCH_DUE_TO_TYPE_PROJECTIONS!>bar<!>()
        }
    }
}
