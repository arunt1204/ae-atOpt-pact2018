/* Current test harness gets confused by packages, but it would be in package Types9l9q;
*/
// Warning: This file is auto-generated from the TeX source of the language spec.
// If you need it changed, work with the specification writers.


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



public class Types9l9q extends x10Test {
   public def run() : boolean = (new Hook()).run();
   public static def main(args:Rail[String]):void {
        new Types9l9q().execute();
    }


// file Types line 1490
 static  class Example {
 static class Thing(p:Long){}
static def example(){
   var x : Thing{x.p==3} = null;
}
}
 static  class Hook{ def run() {Example.example(); return true;}}

}
