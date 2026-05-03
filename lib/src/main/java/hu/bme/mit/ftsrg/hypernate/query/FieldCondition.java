/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import java.util.Arrays;
import java.util.Objects;

/**
 * Intermediate step in the fluent rich query builder that captures a field name and provides
 * type-safe comparison methods.
 *
 * <p>Instances are obtained by calling {@link RichQueryBuilder#where(String)} or {@link
 * RichQueryBuilder#and(String)} and are not intended to be constructed directly.
 *
 * <p>Each comparison method records a {@link Condition} in the parent builder and returns the
 * builder so that chaining can continue:
 *
 * <pre>{@code
 * registry.query(Asset.class)
 *         .where("color").is("blue")       // FieldCondition → RichQueryBuilder
 *         .and("size").greaterThan(10)      // FieldCondition → RichQueryBuilder
 *         .execute();
 * }</pre>
 *
 * @param <T> the entity type being queried
 */
public class FieldCondition<T> {

  private final RichQueryBuilder<T> builder;
  private final String fieldName;

  FieldCondition(final RichQueryBuilder<T> builder, final String fieldName) {
    Objects.requireNonNull(builder, "builder must not be null");
    Objects.requireNonNull(fieldName, "fieldName must not be null");
    if (fieldName.isBlank()) {
      throw new IllegalArgumentException("fieldName must not be blank");
    }
    this.builder = builder;
    this.fieldName = fieldName;
  }

  // ---------- Equality / inequality ----------

  /**
   * Matches entities where the field value equals the given value.
   *
   * <p>CouchDB operator: {@code $eq}
   *
   * @param value the expected value
   * @return the parent query builder for further chaining
   */
  public RichQueryBuilder<T> is(final Object value) {
    return addCondition(QueryOperator.EQ, value);
  }

  /**
   * Matches entities where the field value does <em>not</em> equal the given value.
   *
   * <p>CouchDB operator: {@code $ne}
   *
   * @param value the value to exclude
   * @return the parent query builder for further chaining
   */
  public RichQueryBuilder<T> isNot(final Object value) {
    return addCondition(QueryOperator.NE, value);
  }

  // ---------- Relational ----------

  /**
   * Matches entities where the field value is strictly greater than the given value.
   *
   * <p>CouchDB operator: {@code $gt}
   *
   * @param value the lower bound (exclusive)
   * @return the parent query builder for further chaining
   */
  public RichQueryBuilder<T> greaterThan(final Object value) {
    return addCondition(QueryOperator.GT, value);
  }

  /**
   * Matches entities where the field value is greater than or equal to the given value.
   *
   * <p>CouchDB operator: {@code $gte}
   *
   * @param value the lower bound (inclusive)
   * @return the parent query builder for further chaining
   */
  public RichQueryBuilder<T> greaterThanOrEqual(final Object value) {
    return addCondition(QueryOperator.GTE, value);
  }

  /**
   * Matches entities where the field value is strictly less than the given value.
   *
   * <p>CouchDB operator: {@code $lt}
   *
   * @param value the upper bound (exclusive)
   * @return the parent query builder for further chaining
   */
  public RichQueryBuilder<T> lessThan(final Object value) {
    return addCondition(QueryOperator.LT, value);
  }

  /**
   * Matches entities where the field value is less than or equal to the given value.
   *
   * <p>CouchDB operator: {@code $lte}
   *
   * @param value the upper bound (inclusive)
   * @return the parent query builder for further chaining
   */
  public RichQueryBuilder<T> lessThanOrEqual(final Object value) {
    return addCondition(QueryOperator.LTE, value);
  }

  // ---------- Set membership ----------

  /**
   * Matches entities where the field value is one of the given values.
   *
   * <p>CouchDB operator: {@code $in}
   *
   * @param values one or more candidate values
   * @return the parent query builder for further chaining
   * @throws IllegalArgumentException if no values are supplied
   */
  public RichQueryBuilder<T> in(final Object... values) {
    if (values == null || values.length == 0) {
      throw new IllegalArgumentException("At least one value must be supplied for 'in'");
    }
    return addCondition(QueryOperator.IN, Arrays.asList(values));
  }

  /**
   * Matches entities where the field value is <em>not</em> any of the given values.
   *
   * <p>CouchDB operator: {@code $nin}
   *
   * @param values one or more values to exclude
   * @return the parent query builder for further chaining
   * @throws IllegalArgumentException if no values are supplied
   */
  public RichQueryBuilder<T> notIn(final Object... values) {
    if (values == null || values.length == 0) {
      throw new IllegalArgumentException("At least one value must be supplied for 'notIn'");
    }
    return addCondition(QueryOperator.NIN, Arrays.asList(values));
  }

  // ---------- Existence ----------

  /**
   * Matches entities where the field exists.
   *
   * <p>CouchDB operator: {@code $exists: true}
   *
   * @return the parent query builder for further chaining
   */
  public RichQueryBuilder<T> exists() {
    return addCondition(QueryOperator.EXISTS, true);
  }

  /**
   * Matches entities where the field does <em>not</em> exist.
   *
   * <p>CouchDB operator: {@code $exists: false}
   *
   * @return the parent query builder for further chaining
   */
  public RichQueryBuilder<T> doesNotExist() {
    return addCondition(QueryOperator.EXISTS, false);
  }

  // ---------- Pattern matching ----------

  /**
   * Matches entities where the field value matches the given regular expression.
   *
   * <p>CouchDB operator: {@code $regex}
   *
   * @param pattern the regular expression pattern
   * @return the parent query builder for further chaining
   */
  public RichQueryBuilder<T> matches(final String pattern) {
    Objects.requireNonNull(pattern, "regex pattern must not be null");
    return addCondition(QueryOperator.REGEX, pattern);
  }

  // ---------- Internal ----------

  private RichQueryBuilder<T> addCondition(final QueryOperator operator, final Object value) {
    builder.addCondition(new Condition(fieldName, operator, value));
    return builder;
  }
}
