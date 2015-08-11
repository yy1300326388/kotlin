fun Int?.wrapper() = object {
    val real = this@wrapper != null
    val value = this@wrapper ?: 42
}

class My {
    val instance = My()

    val equalsInstance = (this == instance)

    val isInstance = (this === instance)
}
