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

package apgas;

import java.util.Collection;

/**
 * A {@link MultipleException} is thrown by the {@code finish} construct upon
 * termination when the code or tasks in its scope have uncaught exceptions. The
 * uncaught exceptions may be retrieved using the {@link #getSuppressed()}
 * method of this {@link MultipleException}.
 */
public class MultipleException extends RuntimeException {
  private static final long serialVersionUID = 5931977184541245168L;

  /**
   * Constructs a new {@link MultipleException} from the specified
   * {@code exceptions}.
   *
   * @param exceptions
   *          the uncaught exceptions that contributed to this
   *          {@code MultipleException}
   */
  public MultipleException(Collection<Throwable> exceptions) {
    for (final Throwable t : exceptions) {
      addSuppressed(t);
    }
  }

  /**
   * Returns true if every suppressed exception is a DeadPlaceException.
   *
   * @return true if every suppressed exception is a DeadPlaceException
   */
  public boolean isDeadPlaceException() {
    for (final Throwable t : getSuppressed()) {
      if (t instanceof DeadPlaceException || t instanceof MultipleException
          && ((MultipleException) t).isDeadPlaceException()) {
        continue;
      }
      return false;
    }
    return true;
  }
}
