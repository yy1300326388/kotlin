class My {
    var x = 1
        set(value) {
            $x = value
        }

    var y: Int
        set(value) {
            $y = value + w
        }

    var z: Int
        set(value) {
            $z = value + w
        }

    init {
        // Writing backing fields is safe
        $y = 2
        this.$z = 3
        // But writing properties using setters is dangerous
        <!DANGEROUS_METHOD_CALL_IN_CONSTRUCTOR!>y<!> = 4
        <!DANGEROUS_THIS_IN_CONSTRUCTOR!>this<!>.z = 5
    }

    val w = 6
}