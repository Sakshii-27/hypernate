/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import lombok.experimental.StandardException;

/**
 * Exception thrown when a rich query fails to build or execute.
 *
 * <p>This may be caused by an invalid selector, a missing index, or an error returned by the
 * underlying CouchDB state database.
 */
@StandardException
public class RichQueryException extends HypernateException {}
