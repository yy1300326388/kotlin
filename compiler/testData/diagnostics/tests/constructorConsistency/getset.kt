class My(var x: Int) {    

    var y: Int
        get() = if (x > 0) x else z
        set(arg: Int) {
            if (arg > 0) x = arg
        }

    val z: Int

    init {
        // Dangerous: getter!
        if (<!DANGEROUS_METHOD_CALL_IN_CONSTRUCTOR!>y<!> > 0) z = <!DANGEROUS_THIS_IN_CONSTRUCTOR!>this<!>.y
        // Dangerous: setter!
        <!DANGEROUS_METHOD_CALL_IN_CONSTRUCTOR!>y<!> = 42
        z = -1
    }
}
