import kotlin.platform.platformStatic

class A {
    class object {
        platformStatic fun a1() {

        }
    }

    object A {
        platformStatic fun a2() {

        }
    }

    fun test() {
        val <warning descr="[UNUSED_VARIABLE] Variable 's' is never used">s</warning> = object {
            <error descr="[PLATFORM_STATIC_NOT_IN_OBJECT] Only functions in named objects and class objects of classes can be annotated with 'platformStatic'">platformStatic fun a3()</error> {

            }
        }
    }

    <error descr="[PLATFORM_STATIC_NOT_IN_OBJECT] Only functions in named objects and class objects of classes can be annotated with 'platformStatic'">platformStatic fun a4()</error> {

    }
}

trait B {
    class object {
        <error descr="[PLATFORM_STATIC_NOT_IN_OBJECT] Only functions in named objects and class objects of classes can be annotated with 'platformStatic'">platformStatic fun a1()</error> {

        }
    }

    object A {
        platformStatic fun a2() {

        }
    }

    fun test() {
        val <warning descr="[UNUSED_VARIABLE] Variable 's' is never used">s</warning> = object {
            <error descr="[PLATFORM_STATIC_NOT_IN_OBJECT] Only functions in named objects and class objects of classes can be annotated with 'platformStatic'">platformStatic fun a3()</error> {

            }
        }
    }

    <error descr="[PLATFORM_STATIC_NOT_IN_OBJECT] Only functions in named objects and class objects of classes can be annotated with 'platformStatic'">platformStatic fun a4()</error> {

    }
}