open class Base {
    open var x: Int

    open var y: Int

    constructor() {
        <!DANGEROUS_OPEN_PROPERTY_ACCESS_IN_CONSTRUCTOR!>x<!> = 42
        (@fragile this).y = 24
        val temp = this.<!DANGEROUS_OPEN_PROPERTY_ACCESS_IN_CONSTRUCTOR!>x<!>
        this.<!DANGEROUS_OPEN_PROPERTY_ACCESS_IN_CONSTRUCTOR!>x<!> = @fragile y
        <!DANGEROUS_OPEN_PROPERTY_ACCESS_IN_CONSTRUCTOR!>y<!> = temp

    }
}
