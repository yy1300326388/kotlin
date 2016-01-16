//KT-510 `this.` allows initialization without backing field

package kt510

public open class Identifier1() {
    final var field : Boolean
    init {
        field = false; // error
    }
}


public open class Identifier2() {
    final var field : Boolean
    init {
        this.field = false;
    }
}
