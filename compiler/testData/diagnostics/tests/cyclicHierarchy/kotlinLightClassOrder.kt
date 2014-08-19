// FILE: AClass.kt
class AClass: SomeTraitImpl() {
    override fun hi(): Any = 1
}

// FILE: SomeTrait.kt
trait SomeTrait {
    fun foo()
}

// FILE: SomeTraitImpl.java
public class SomeTraitImpl implements SomeTrait {
    @Override
    public void foo() {}

    public Object hi() {
    return null;
    }
}

/*
 * Resolve hi() triggers building LightClass for SomeTraitImpl
 *
 *
 */