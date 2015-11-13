package test

@Target(AnnotationTarget.TYPE)
annotation class Anno(val c: Int = 1)

abstract class AnnotatedTypes {
    abstract fun f(i: @Anno Int, j: @Anno Int?)

    val nullable: @Anno(1) Int? = null
//    abstract val list: @Anno(1) List<@Anno(1) Int>
//    abstract val map: @Anno(1) Map<@Anno(1) Int, @Anno(1) Int>
//    abstract val nullableMap: Map<@Anno(1) Int?, @Anno(1) Int?>?
//    abstract val projections: Map<in @Anno(1) Int, out @Anno(1) String>
//    val function: () -> Unit = {}
//    abstract val functionWithParam: (@Anno(1) String, @Anno(1) Int) -> @Anno(1) List<@Anno(1) String>
//    abstract val extFunction: @Anno(1) String.() -> @Anno(1) List<@Anno(1) String>
//    abstract val extFunctionWithParam: @Anno(1) String.(@Anno(1) Int, @Anno(1) String) -> @Anno(1) List<@Anno(1) String>
//
//    abstract val extFunctionWithNullables: @Anno String.(Int?, @Anno String?) -> List<@Anno String?>?
//    abstract val deepExtFunctionType: @Anno String.((Int) -> Int, @Anno String?) -> List<@Anno String?>?
//
//    public fun <P1, P2, P3, R> Function3<@Anno(1) P1, @Anno(1) P2, @Anno(1) P3, @Anno(1) R>.extOnFunctionType() {
//    }
//
//    abstract val extFun: @Extension @Anno(1) Function2<@Anno(1) Int, @Anno(1) Int, @Anno(1) Unit>
//    abstract val listExtStarFun: @Anno(1) List<@Extension @Anno(1) Function1<*, *>>
//    abstract val funTypeWithStarAndNonStar: @Anno(1) Function1<*, @Anno(1) Int>
}