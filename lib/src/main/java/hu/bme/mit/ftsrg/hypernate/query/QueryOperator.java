/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

/**
 * CouchDB (Mango) query operators supported by the rich query builder.
 *
 * <p>Each constant maps to its corresponding CouchDB operator string (e.g. {@code "$eq"}, {@code
 * "$gt"}).
 *
 * @see <a href="https://docs.couchdb.org/en/stable/api/database/find.html#condition-operators">
 *     CouchDB Condition Operators</a>
 */
enum QueryOperator {

  /** Equality: field value must equal the operand. */
  EQ("$eq"),

  /** Not equal: field value must not equal the operand. */
  NE("$ne"),

  /** Greater than: field value must be strictly greater than the operand. */
  GT("$gt"),

  /** Greater than or equal: field value must be &ge; the operand. */
  GTE("$gte"),

  /** Less than: field value must be strictly less than the operand. */
  LT("$lt"),

  /** Less than or equal: field value must be &le; the operand. */
  LTE("$lte"),

  /** Inclusion: field value must be one of the supplied operands. */
  IN("$in"),

  /** Exclusion: field value must not be any of the supplied operands. */
  NIN("$nin"),

  /** Existence: matches documents where the field exists (or does not). */
  EXISTS("$exists"),

  /** Regular expression: field value must match the supplied pattern. */
  REGEX("$regex");

  private final String couchDbOp;

  QueryOperator(final String couchDbOp) {
    this.couchDbOp = couchDbOp;
  }

  /**
   * Returns the CouchDB JSON operator string.
   *
   * @return the operator string, e.g. {@code "$eq"}
   */
  String toCouchDbOp() {
    return couchDbOp;
  }
}
