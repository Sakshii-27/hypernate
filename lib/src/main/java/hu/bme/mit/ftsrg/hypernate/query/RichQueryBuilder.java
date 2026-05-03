/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import hu.bme.mit.ftsrg.hypernate.annotations.QueryIndex;
import hu.bme.mit.ftsrg.hypernate.util.JSON;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.hyperledger.fabric.protos.peer.QueryResponseMetadata;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIteratorWithMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A fluent, composable builder for CouchDB rich queries against the Hyperledger Fabric world state.
 *
 * <p>Instances are created through {@link
 * hu.bme.mit.ftsrg.hypernate.registry.Registry#query(Class)} and support a natural, chainable API:
 *
 * <pre>{@code
 * List<Asset> results = registry.query(Asset.class)
 *     .where("color").is("blue")
 *     .and("size").greaterThan(10)
 *     .and("owner").in("Alice", "Bob")
 *     .sortBy("value", SortOrder.DESC)
 *     .limit(50)
 *     .execute();
 * }</pre>
 *
 * <h3>How it works</h3>
 *
 * <ol>
 *   <li>{@link #where(String)} / {@link #and(String)} return a {@link FieldCondition} that captures
 *       the target field name.
 *   <li>Calling a comparison method on {@code FieldCondition} (e.g. {@code .is()}, {@code
 *       .greaterThan()}) records a {@link Condition} and returns this builder.
 *   <li>{@link #sortBy(String, SortOrder)} and {@link #limit(int)} configure ordering and
 *       pagination.
 *   <li>{@link #execute()} builds a CouchDB Mango selector via {@link CouchDbSelector}, passes it
 *       to {@link ChaincodeStub#getQueryResult(String)}, and deserializes each result into an
 *       entity of type {@code T}.
 * </ol>
 *
 * <p><b>Thread safety:</b> This class is <em>not</em> thread-safe and is intended for single-use
 * within a transaction.
 *
 * @param <T> the entity type being queried
 */
public class RichQueryBuilder<T> {

  private static final Logger logger = LoggerFactory.getLogger(RichQueryBuilder.class);

  private final ChaincodeStub stub;
  private final Class<T> entityClass;
  private final String docType;

  private final List<Condition> conditions = new ArrayList<>();
  private final List<SortField> sortFields = new ArrayList<>();
  private int limit = -1;
  private String bookmark;

  /**
   * Creates a new query builder for the given entity class.
   *
   * @param stub the chaincode stub used to execute the query
   * @param entityClass the entity class whose instances are being queried
   * @param docType the document type identifier stored in CouchDB (typically the uppercased
   *     fully-qualified class name, matching the key used by {@code Registry})
   */
  public RichQueryBuilder(
      final ChaincodeStub stub, final Class<T> entityClass, final String docType) {
    Objects.requireNonNull(stub, "stub must not be null");
    Objects.requireNonNull(entityClass, "entityClass must not be null");
    Objects.requireNonNull(docType, "docType must not be null");
    this.stub = stub;
    this.entityClass = entityClass;
    this.docType = docType;
  }

  // ---------- Condition chaining ----------

  /**
   * Begins a condition clause for the specified field.
   *
   * <p>This is typically the first call after {@code registry.query(...)}.
   *
   * @param fieldName the entity field to apply a condition to
   * @return a {@link FieldCondition} to select the comparison operator
   */
  public FieldCondition<T> where(final String fieldName) {
    return new FieldCondition<>(this, fieldName);
  }

  /**
   * Begins an additional condition clause for the specified field.
   *
   * <p>Semantically equivalent to {@link #where(String)} — all conditions are combined with
   * implicit {@code AND} (the CouchDB default).
   *
   * @param fieldName the entity field to apply a condition to
   * @return a {@link FieldCondition} to select the comparison operator
   */
  public FieldCondition<T> and(final String fieldName) {
    return new FieldCondition<>(this, fieldName);
  }

  // ---------- Sort ----------

  /**
   * Adds a sort specification to the query.
   *
   * <p>Multiple sort fields can be added; they are applied in the order specified.
   *
   * @param fieldName the field to sort by
   * @param order ascending or descending
   * @return this builder for further chaining
   */
  public RichQueryBuilder<T> sortBy(final String fieldName, final SortOrder order) {
    Objects.requireNonNull(fieldName, "sort fieldName must not be null");
    Objects.requireNonNull(order, "sort order must not be null");
    sortFields.add(new SortField(fieldName, order));
    return this;
  }

  // ---------- Pagination ----------

  /**
   * Sets the maximum number of results to return.
   *
   * @param limit the result limit (must be positive)
   * @return this builder for further chaining
   * @throws IllegalArgumentException if limit is not positive
   */
  public RichQueryBuilder<T> limit(final int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive, got " + limit);
    }
    this.limit = limit;
    return this;
  }

  /**
   * Sets a pagination bookmark for retrieving the next page of results.
   *
   * <p>Bookmarks are obtained from a previous query's metadata and allow efficient pagination
   * through large result sets.
   *
   * @param bookmark the pagination bookmark string
   * @return this builder for further chaining
   */
  public RichQueryBuilder<T> bookmark(final String bookmark) {
    this.bookmark = bookmark;
    return this;
  }

  // ---------- Execution ----------

  /**
   * Builds the CouchDB selector and executes the query against the ledger.
   *
   * <p>When a {@link #limit(int)} or {@link #bookmark(String)} has been set, this method
   * automatically uses Fabric's {@code getQueryResultWithPagination} API for correct pagination
   * semantics. Otherwise it delegates to the simpler {@code getQueryResult}.
   *
   * <p>Note: Calling {@code execute()} on a builder with no conditions will return all entities of
   * this type, similar to {@link hu.bme.mit.ftsrg.hypernate.registry.Registry#readAll(Class)}.
   *
   * @return an unmodifiable list of matching entities (may be empty, never {@code null})
   * @throws RichQueryException if the query cannot be built or if execution fails
   */
  public List<T> execute() {
    warnIfMissingIndices();
    final String queryJson = buildSelectorJson();
    logger.debug("Executing rich query: {}", queryJson);

    try {
      final Iterable<KeyValue> iterable;
      if (limit > 0 || (bookmark != null && !bookmark.isBlank())) {
        final int pageSize = limit > 0 ? limit : Integer.MAX_VALUE;
        final String bm = bookmark != null ? bookmark : "";
        iterable = () -> stub.getQueryResultWithPagination(queryJson, pageSize, bm).iterator();
      } else {
        iterable = () -> stub.getQueryResult(queryJson).iterator();
      }

      final List<T> results =
          StreamSupport.stream(iterable.spliterator(), false)
              .map(
                  kv -> {
                    final byte[] value = kv.getValue();
                    logger.debug("Query hit: key={}, valueLen={}", kv.getKey(), value.length);
                    return JSON.deserialize(new String(value, StandardCharsets.UTF_8), entityClass);
                  })
              .collect(Collectors.toList());

      logger.debug("Rich query returned {} result(s)", results.size());
      return Collections.unmodifiableList(results);
    } catch (Exception e) {
      throw new RichQueryException("Failed to execute rich query", e);
    }
  }

  /**
   * Builds the CouchDB selector and executes a paginated query, returning results along with
   * pagination metadata.
   *
   * <p>This method always uses Fabric's {@code getQueryResultWithPagination} API. The returned
   * {@link QueryPage} includes the bookmark for the next page and the count of fetched records,
   * enabling cursor-based iteration through large result sets:
   *
   * <pre>{@code
   * QueryPage<Asset> page = registry.query(Asset.class)
   *     .where("color").is("blue")
   *     .limit(10)
   *     .executePaged();
   *
   * List<Asset> items = page.results();
   * String nextBookmark = page.bookmark();
   * }</pre>
   *
   * @return a {@link QueryPage} containing the results and pagination metadata
   * @throws RichQueryException if the query cannot be built or if execution fails
   */
  public QueryPage<T> executePaged() {
    warnIfMissingIndices();
    final String queryJson = buildSelectorJson();
    logger.debug("Executing paginated rich query: {}", queryJson);

    try {
      final int pageSize = limit > 0 ? limit : Integer.MAX_VALUE;
      final String bm = bookmark != null ? bookmark : "";
      final QueryResultsIteratorWithMetadata<KeyValue> resultIterator =
          stub.getQueryResultWithPagination(queryJson, pageSize, bm);

      final Iterable<KeyValue> iterable = resultIterator::iterator;
      final List<T> results =
          StreamSupport.stream(iterable.spliterator(), false)
              .map(
                  kv -> {
                    final byte[] value = kv.getValue();
                    logger.debug("Query hit: key={}, valueLen={}", kv.getKey(), value.length);
                    return JSON.deserialize(new String(value, StandardCharsets.UTF_8), entityClass);
                  })
              .collect(Collectors.toList());

      final QueryResponseMetadata metadata = resultIterator.getMetadata();
      final String nextBookmark = metadata != null ? metadata.getBookmark() : "";
      final int fetchedCount =
          metadata != null ? metadata.getFetchedRecordsCount() : results.size();

      logger.debug(
          "Paginated query returned {} result(s), nextBookmark={}", results.size(), nextBookmark);
      return new QueryPage<>(Collections.unmodifiableList(results), nextBookmark, fetchedCount);
    } catch (Exception e) {
      throw new RichQueryException("Failed to execute paginated rich query", e);
    }
  }

  // ---------- Introspection ----------

  /**
   * Returns the CouchDB Mango selector JSON without executing the query.
   *
   * <p>Useful for debugging, logging, or passing the selector to external systems.
   *
   * @return the JSON query string
   */
  public String buildSelectorJson() {
    return CouchDbSelector.build(docType, conditions, sortFields, limit, bookmark);
  }

  /**
   * Returns an unmodifiable view of the conditions accumulated so far.
   *
   * @return the list of conditions
   */
  List<Condition> getConditions() {
    return Collections.unmodifiableList(conditions);
  }

  /**
   * Returns an unmodifiable view of the sort specifications accumulated so far.
   *
   * @return the list of sort fields
   */
  List<SortField> getSortFields() {
    return Collections.unmodifiableList(sortFields);
  }

  /**
   * Returns the configured result limit, or {@code -1} if no limit has been set.
   *
   * @return the limit
   */
  public int getLimit() {
    return limit;
  }

  // ---------- Package-private ----------

  /**
   * Records a condition from a {@link FieldCondition}.
   *
   * @param condition the condition to add
   */
  void addCondition(final Condition condition) {
    conditions.add(condition);
  }

  // ---------- Index validation ----------

  /**
   * Logs a warning for each queried or sorted field that does not appear in any {@link QueryIndex}
   * annotation on the entity class.
   *
   * <p>This is a best-effort hint — missing indices will not prevent the query from executing, but
   * CouchDB will perform a full scan which can be very slow on large datasets.
   */
  private void warnIfMissingIndices() {
    final QueryIndex[] indices = entityClass.getAnnotationsByType(QueryIndex.class);
    if (indices.length == 0) {
      if (!conditions.isEmpty() || !sortFields.isEmpty()) {
        logger.warn(
            "Entity {} has no @QueryIndex annotations — consider adding indices for queried fields",
            entityClass.getSimpleName());
      }
      return;
    }

    final Set<String> indexedFields =
        Arrays.stream(indices)
            .flatMap(idx -> Arrays.stream(idx.attributes()))
            .map(attr -> attr.name())
            .collect(Collectors.toSet());

    for (final Condition cond : conditions) {
      if (!indexedFields.contains(cond.fieldName())) {
        logger.warn(
            "Field '{}' is used in a query condition on {} but is not covered by any"
                + " @QueryIndex — this may result in a full collection scan",
            cond.fieldName(),
            entityClass.getSimpleName());
      }
    }

    for (final SortField sf : sortFields) {
      if (!indexedFields.contains(sf.fieldName())) {
        logger.warn(
            "Field '{}' is used in a sort clause on {} but is not covered by any @QueryIndex",
            sf.fieldName(),
            entityClass.getSimpleName());
      }
    }
  }
}
