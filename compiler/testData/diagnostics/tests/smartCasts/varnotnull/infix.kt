// See KT-774
fun box() : Int {
    var a : Int? = 1
    var d = 1

    return <!DEBUG_INFO_SMARTCAST!>a<!> + d
}