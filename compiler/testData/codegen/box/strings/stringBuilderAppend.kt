fun box() : String {

    val s = "1" + "2" + 3 + 4L + 5.0 + 6F + '7'

    if (s != "12345.06.07") return "fail $s"

    return "OK"
}

