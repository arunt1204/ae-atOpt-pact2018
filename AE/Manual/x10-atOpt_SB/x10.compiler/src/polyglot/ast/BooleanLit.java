/*
 *  This file is part of the X10 project (http://x10-lang.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 * This file was originally derived from the Polyglot extensible compiler framework.
 *
 *  (C) Copyright 2000-2007 Polyglot project group, Cornell University
 *  (C) Copyright IBM Corporation 2007-2016.
 */

package polyglot.ast;

/**
 * A <code>BooleanLit</code> represents a boolean literal expression.
 */
public interface BooleanLit extends Lit
{
    /**
     * The literal's value.
     */
    boolean value();

    /**
     * Set the literal's value.
     */
    BooleanLit value(boolean value);
}
