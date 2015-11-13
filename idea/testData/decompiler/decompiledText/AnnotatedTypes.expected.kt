// IntelliJ API Decompiler stub source generated from a class file
// Implementation of methods is not available

package test

public abstract class AnnotatedTypes public constructor() {
    public abstract val deepExtFunctionType: @test.Anno kotlin.String.((kotlin.Int) -> kotlin.Int, kotlin.String?) -> kotlin.List<@test.Anno kotlin.String?>?

    public abstract val extFun: @test.Anno kotlin.Int.(@test.Anno kotlin.Int) -> @test.Anno kotlin.Unit

    public abstract val extFunction: @test.Anno kotlin.String.() -> @test.Anno kotlin.List<@test.Anno kotlin.String>

    public abstract val extFunctionWithNullables: @test.Anno kotlin.String.(kotlin.Int?, kotlin.String?) -> kotlin.List<@test.Anno kotlin.String?>?

    public abstract val extFunctionWithParam: @test.Anno kotlin.String.(kotlin.Int, kotlin.String) -> @test.Anno kotlin.List<@test.Anno kotlin.String>

    public abstract val funTypeWithStarAndNonStar: @test.Anno kotlin.Function1<*, @test.Anno kotlin.Int>

    public final val function: () -> kotlin.Unit /* compiled code */

    public abstract val functionWithParam: (kotlin.String, kotlin.Int) -> @test.Anno kotlin.List<@test.Anno kotlin.String>

    public abstract val list: @test.Anno kotlin.List<@test.Anno kotlin.Int>

    public abstract val listExtStarFun: @test.Anno kotlin.List<@kotlin.Extension @test.Anno kotlin.Function1<*, *>>

    public abstract val map: @test.Anno kotlin.Map<@test.Anno kotlin.Int, @test.Anno kotlin.Int>

    public final val nullable: @test.Anno kotlin.Int? /* compiled code */

    public abstract val nullableMap: kotlin.Map<@test.Anno kotlin.Int?, @test.Anno kotlin.Int?>?

    public abstract val projections: kotlin.Map<in @test.Anno kotlin.Int, out @test.Anno kotlin.String>

    public abstract fun f(i: @test.Anno kotlin.Int, j: @test.Anno kotlin.Int?): kotlin.Unit

    public final fun <P1, P2, P3, R> ((@test.Anno P1, @test.Anno P2, @test.Anno P3) -> @test.Anno R).extOnFunctionType(): kotlin.Unit { /* compiled code */ }
}

