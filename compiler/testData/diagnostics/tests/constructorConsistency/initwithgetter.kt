class My {
    val x: Int
        get() = $x + z

    val y: Int
        get() = $y - z

    val w: Int

    init {
        // Safe, val never has a setter
        x = 0
        this.y = 0
        // Unsafe
        w = <!DANGEROUS_THIS_IN_CONSTRUCTOR!>this<!>.x + <!DANGEROUS_METHOD_CALL_IN_CONSTRUCTOR!>y<!>
    }

    val z = 1
}
