interface IBase {
    fun copy(): IBase
}

interface ILeft : IBase {
    override fun copy(): ILeft
}

open class CLeft : ILeft {
    override fun copy(): ILeft = CLeft()
}

interface IRight : IBase {
    override fun copy(): IRight
}

// Error: ILeft::copy and IRight::copy have unrelated return types
<!RETURN_TYPE_MISMATCH_ON_INHERITANCE, ABSTRACT_MEMBER_NOT_IMPLEMENTED!>class CDerivedInvalid1<!> : ILeft, IRight

// Error: CLeft::copy and IRight::copy have unrelated return types
<!RETURN_TYPE_MISMATCH_ON_INHERITANCE!>class CDerivedInvalid2<!> : CLeft(), IRight

// OK: CDerived2::copy overrides both ILeft::copy and IRight::copy
class CDerived : ILeft, IRight {
    override fun copy(): CDerived = CDerived()
}