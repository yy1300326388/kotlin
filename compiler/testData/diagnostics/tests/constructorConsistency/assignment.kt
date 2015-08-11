class My {
    val x: Int

    constructor() {
        val temp = <!DANGEROUS_THIS_IN_CONSTRUCTOR!>this<!>
        x = bar(temp)
    }

}

fun bar(arg: My): Int = arg.x
