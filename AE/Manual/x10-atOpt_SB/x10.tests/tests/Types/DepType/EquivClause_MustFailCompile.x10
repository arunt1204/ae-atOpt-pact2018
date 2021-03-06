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
 * Checks that decplauses are checked when checking type equality.
 *
 * @author vj
 */
public class EquivClause_MustFailCompile extends x10Test {
    var i: int{self==1n} = 1n;
    var j: int{self==0n} = 1n; // ERR

	public def run(): boolean {
	   
	    return true;
	}
	public static def main(var args: Rail[String]): void {
		new EquivClause_MustFailCompile().execute();
	}
	
}
