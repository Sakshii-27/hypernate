/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collection;
import java.util.List;

/**
 * Builds a CouchDB Mango query JSON string from the components accumulated in a {@link
 * RichQueryBuilder}.
 *
 * <p>The generated JSON follows the <a
 * href="https://docs.couchdb.org/en/stable/api/database/find.html">CouchDB {@code _find}
 * endpoint</a> format, which is also the format expected by Hyperledger Fabric's {@code
 * ChaincodeStub#getQueryResult(String)}.
 *
 * <h3>Example output</h3>
 *
 * <pre>{@code
 * {
 *   "selector": {
 *     "docType": "ASSET",
 *     "color": { "$eq": "blue" },
 *     "size": { "$gt": 10 },
 *     "owner": { "$in": ["Alice", "Bob"] }
 *   },
 *   "sort": [ { "value": "desc" } ],
 *   "limit": 50
 * }
 * }</pre>
 */
final class CouchDbSelector {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private CouchDbSelector() {
    // utility class
  }

  /**
   * Builds a complete CouchDB Mango query JSON string.
   *
   * @param docType the entity type identifier used to scope results (stored in the {@code
   *     "docType"} field of each document)
   * @param conditions the list of field conditions
   * @param sortFields the list of sort specifications (may be empty)
   * @param limit the maximum number of results, or {@code -1} for no limit
   * @param bookmark the pagination bookmark, or {@code null} for no bookmark
   * @return the JSON query string
   * @throws RichQueryException if JSON serialization fails
   */
  static String build(
      final String docType,
      final List<Condition> conditions,
      final List<SortField> sortFields,
      final int limit,
      final String bookmark) {

    final ObjectNode root = MAPPER.createObjectNode();
    final ObjectNode selector = buildSelector(docType, conditions);
    root.set("selector", selector);

    if (!sortFields.isEmpty()) {
      root.set("sort", buildSort(sortFields));
    }

    if (limit > 0) {
      root.put("limit", limit);
    }

    if (bookmark != null && !bookmark.isBlank()) {
      root.put("bookmark", bookmark);
    }

    try {
      return MAPPER.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new RichQueryException("Failed to serialize CouchDB selector to JSON", e);
    }
  }

  // ---------- Internal helpers ----------

  private static ObjectNode buildSelector(final String docType, final List<Condition> conditions) {
    final ObjectNode selector = MAPPER.createObjectNode();

    // scope to entity type
    selector.put("docType", docType);

    for (final Condition cond : conditions) {
      addCondition(selector, cond);
    }

    return selector;
  }

  private static void addCondition(final ObjectNode selector, final Condition cond) {
    final String field = cond.fieldName();
    final QueryOperator op = cond.operator();
    final Object value = cond.value();

    // If the field already has conditions, merge the new operator into the existing object node.
    // This correctly handles range queries like:
    // .where("size").greaterThan(10).and("size").lessThan(100)
    // which produces: "size": {"$gt": 10, "$lt": 100}
    final JsonNode existing = selector.get(field);
    final ObjectNode opNode;
    if (existing != null && existing.isObject()) {
      opNode = (ObjectNode) existing;
    } else {
      opNode = MAPPER.createObjectNode();
    }
    putValue(opNode, op.toCouchDbOp(), value);
    selector.set(field, opNode);
  }

  private static void putValue(final ObjectNode node, final String key, final Object value) {
    if (value == null) {
      node.putNull(key);
    } else if (value instanceof String s) {
      node.put(key, s);
    } else if (value instanceof Integer i) {
      node.put(key, i);
    } else if (value instanceof Long l) {
      node.put(key, l);
    } else if (value instanceof Double d) {
      node.put(key, d);
    } else if (value instanceof Float f) {
      node.put(key, f);
    } else if (value instanceof Boolean b) {
      node.put(key, b);
    } else if (value instanceof Collection<?> coll) {
      final ArrayNode arr = MAPPER.createArrayNode();
      for (final Object elem : coll) {
        addArrayElement(arr, elem);
      }
      node.set(key, arr);
    } else {
      // fall back to toString for unknown types
      node.put(key, value.toString());
    }
  }

  private static void addArrayElement(final ArrayNode arr, final Object elem) {
    if (elem == null) {
      arr.addNull();
    } else if (elem instanceof String s) {
      arr.add(s);
    } else if (elem instanceof Integer i) {
      arr.add(i);
    } else if (elem instanceof Long l) {
      arr.add(l);
    } else if (elem instanceof Double d) {
      arr.add(d);
    } else if (elem instanceof Float f) {
      arr.add(f);
    } else if (elem instanceof Boolean b) {
      arr.add(b);
    } else {
      arr.add(elem.toString());
    }
  }

  private static ArrayNode buildSort(final List<SortField> sortFields) {
    final ArrayNode sortArray = MAPPER.createArrayNode();
    for (final SortField sf : sortFields) {
      final ObjectNode entry = MAPPER.createObjectNode();
      entry.put(sf.fieldName(), sf.order().toCouchDbValue());
      sortArray.add(entry);
    }
    return sortArray;
  }
}
