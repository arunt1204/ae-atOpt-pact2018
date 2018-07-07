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

package x10.compiler;

import x10.lang.annotations.ClassAnnotation;
import x10.lang.annotations.FieldAnnotation;
import x10.lang.annotations.MethodAnnotation;

/**
 * An annotation to mark classes, fields, or methods that don't appear in the source code.
 */
public interface Synthetic extends ClassAnnotation, FieldAnnotation, MethodAnnotation { }
