/*
 *  This file is part of the X10 project (http://x10-lang.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  (C) Copyright IBM Corporation 2006-2016.
 */

import harness.x10Test;

/**

 */

class XTENLANG_731 extends x10Test {

	class A (x:Long) {
		def this(x:Long){property(x);}
		operator this()=x;
	}

	public def run(): boolean {
		val v = new A(1);
		return v()==1;
	
	}

    public static def main(Rail[String]) {
        new XTENLANG_731().execute();
    }
}
