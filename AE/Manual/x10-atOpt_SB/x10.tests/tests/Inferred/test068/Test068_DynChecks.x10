/*
 * Fields.
 *
 */
//OPTIONS: -STATIC_CHECKS=false -CONSTRAINT_INFERENCE=false -VERBOSE_INFERENCE=true



import harness.x10Test;
import x10.compiler.InferGuard;

public class Test068_DynChecks extends x10Test {
    static def assertEq (x: Long, y: Long){ x == y } {}
    static def assertEqB (x: B, y: B){ x == y } {}

    @InferGuard
    static def f (a: A, b: B) {
	assertEq(a.a.b, b.b);
	assertEqB(a.a, b);
    }

    public def run(): boolean {
	val b = new B(0);
	val a = new A(b);
	Test068_DynChecks.f(a, b);
        return true;
    }

    public static def main(Rail[String]) {
    	new Test068_DynChecks().execute();
    }

}
