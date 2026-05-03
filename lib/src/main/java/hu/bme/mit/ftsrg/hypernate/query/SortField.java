/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

/**
 * An immutable representation of a sort specification in a rich query.
 *
 * @param fieldName the entity field to sort by
 * @param order the sort direction
 */
record SortField(String fieldName, SortOrder order) {}
