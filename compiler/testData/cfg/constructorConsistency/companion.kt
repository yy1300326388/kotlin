class My {

    val x = foo()

    val w = bar()

    fun foo() = 0

    companion object {
        
        val y = foo()

        val z: Int? = bar()

        fun bar() = 1
    }
}