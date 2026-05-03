/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

/**
 * An immutable representation of a single field condition in a rich query.
 *
 * <p>A condition binds a {@link #fieldName} to a {@link QueryOperator} and one or more operand
 * values. Conditions are accumulated by the {@link RichQueryBuilder} and later serialized into a
 * CouchDB Mango selector by {@link CouchDbSelector}.
 *
 * @param fieldName the name of the entity field this condition applies to
 * @param operator the comparison operator
 * @param value the operand value (may be a single object or an array/collection for {@link
 *     QueryOperator#IN}/{@link QueryOperator#NIN})
 */
record Condition(String fieldName, QueryOperator operator, Object value) {}
