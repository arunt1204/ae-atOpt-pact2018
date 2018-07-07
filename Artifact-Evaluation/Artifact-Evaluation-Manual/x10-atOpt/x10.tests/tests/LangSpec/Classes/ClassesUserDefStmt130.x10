/* Current test harness gets confused by packages, but it would be in package Classes_UserDefStmt_At1;
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

 import x10.util.*;

public class ClassesUserDefStmt130 extends x10Test {
   public def run() : boolean = (new Hook()).run();
   public static def main(args:Rail[String]):void {
        new ClassesUserDefStmt130().execute();
    }


// file Classes line 3261
 static class Ring {
  val places: PlaceGroup;

  public def this (places: PlaceGroup) {
    this.places = places;
  }

  public operator at(body: ()=>void) {
    at(places.next(here)) { body(); }
  }
}

 static class Hook {
   def run():Boolean = true;
}

}
