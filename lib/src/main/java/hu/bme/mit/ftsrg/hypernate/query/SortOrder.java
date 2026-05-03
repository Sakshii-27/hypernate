/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

/**
 * Sort order for {@link RichQueryBuilder#sortBy(String, SortOrder)} clauses.
 *
 * <p>Corresponds to the {@code "asc"} / {@code "desc"} direction strings in CouchDB selectors.
 */
public enum SortOrder {

  /** Ascending order (smallest value first). */
  ASC("asc"),

  /** Descending order (largest value first). */
  DESC("desc");

  private final String couchDbValue;

  SortOrder(final String couchDbValue) {
    this.couchDbValue = couchDbValue;
  }

  /**
   * Returns the CouchDB JSON string representation of this sort order.
   *
   * @return {@code "asc"} or {@code "desc"}
   */
  public String toCouchDbValue() {
    return couchDbValue;
  }
}
