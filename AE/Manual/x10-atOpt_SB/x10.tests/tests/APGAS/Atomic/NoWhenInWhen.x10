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
 * A when statement cannot occur in a when statement.
 * @vj
 */
public class NoWhenInWhen extends x10Test {
	
	public def run(): boolean {
        try {
        	var lock1:boolean=false;
			when (!lock1) 
			   when (!lock1) 
			      lock1 = true;
		} catch (IllegalOperationException) {
			return true;
		} 
		return false;
	}

	public static def main(Rail[String]){
		new NoWhenInWhen().execute();
	}
}
