/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.bme.mit.ftsrg.hypernate.annotations.AttributeInfo;
import hu.bme.mit.ftsrg.hypernate.annotations.PrimaryKey;
import hu.bme.mit.ftsrg.hypernate.registry.Registry;
import hu.bme.mit.ftsrg.hypernate.util.JSON;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.annotation.Nonnull;
import lombok.experimental.FieldNameConstants;
import org.hyperledger.fabric.protos.peer.QueryResponseMetadata;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.hyperledger.fabric.shim.ledger.QueryResultsIteratorWithMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for the fluent rich query builder API.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Selector JSON generation for every operator
 *   <li>Sort, limit, and bookmark serialization
 *   <li>End-to-end execute() with a mocked stub
 *   <li>Edge cases and validation
 * </ul>
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class RichQueryBuilderTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** A simple entity used across all tests. */
  @FieldNameConstants
  @PrimaryKey({
    @AttributeInfo(name = TestAsset.Fields.assetID),
  })
  private record TestAsset(
      String assetID, String color, int size, int appraisedValue, String owner) {}

  @Mock private ChaincodeStub stub;
  private Registry registry;

  @BeforeEach
  void setup() {
    registry = new Registry(stub);
  }

  // ============================
  // Selector JSON generation
  // ============================

  @Nested
  class selector_json_generation {

    @Test
    void generates_equality_condition() throws JsonProcessingException {
      String json = registry.query(TestAsset.class).where("color").is("blue").buildSelectorJson();

      JsonNode root = MAPPER.readTree(json);
      JsonNode selector = root.get("selector");
      assertNotNull(selector);
      assertEquals("blue", selector.get("color").get("$eq").asText());
    }

    @Test
    void generates_not_equal_condition() throws JsonProcessingException {
      String json = registry.query(TestAsset.class).where("color").isNot("red").buildSelectorJson();

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertEquals("red", selector.get("color").get("$ne").asText());
    }

    @Test
    void generates_greater_than_condition() throws JsonProcessingException {
      String json =
          registry.query(TestAsset.class).where("size").greaterThan(10).buildSelectorJson();

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertEquals(10, selector.get("size").get("$gt").asInt());
    }

    @Test
    void generates_greater_than_or_equal_condition() throws JsonProcessingException {
      String json =
          registry.query(TestAsset.class).where("size").greaterThanOrEqual(10).buildSelectorJson();

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertEquals(10, selector.get("size").get("$gte").asInt());
    }

    @Test
    void generates_less_than_condition() throws JsonProcessingException {
      String json = registry.query(TestAsset.class).where("size").lessThan(100).buildSelectorJson();

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertEquals(100, selector.get("size").get("$lt").asInt());
    }

    @Test
    void generates_less_than_or_equal_condition() throws JsonProcessingException {
      String json =
          registry.query(TestAsset.class).where("size").lessThanOrEqual(100).buildSelectorJson();

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertEquals(100, selector.get("size").get("$lte").asInt());
    }

    @Test
    void generates_in_condition() throws JsonProcessingException {
      String json =
          registry.query(TestAsset.class).where("owner").in("Alice", "Bob").buildSelectorJson();

      JsonNode selector = MAPPER.readTree(json).get("selector");
      JsonNode inNode = selector.get("owner").get("$in");
      assertTrue(inNode.isArray());
      assertEquals(2, inNode.size());
      assertEquals("Alice", inNode.get(0).asText());
      assertEquals("Bob", inNode.get(1).asText());
    }

    @Test
    void generates_not_in_condition() throws JsonProcessingException {
      String json = registry.query(TestAsset.class).where("owner").notIn("Eve").buildSelectorJson();

      JsonNode selector = MAPPER.readTree(json).get("selector");
      JsonNode ninNode = selector.get("owner").get("$nin");
      assertTrue(ninNode.isArray());
      assertEquals(1, ninNode.size());
      assertEquals("Eve", ninNode.get(0).asText());
    }

    @Test
    void generates_exists_condition() throws JsonProcessingException {
      String json = registry.query(TestAsset.class).where("owner").exists().buildSelectorJson();

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertTrue(selector.get("owner").get("$exists").asBoolean());
    }

    @Test
    void generates_does_not_exist_condition() throws JsonProcessingException {
      String json =
          registry.query(TestAsset.class).where("owner").doesNotExist().buildSelectorJson();

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertFalse(selector.get("owner").get("$exists").asBoolean());
    }

    @Test
    void generates_regex_condition() throws JsonProcessingException {
      String json =
          registry.query(TestAsset.class).where("color").matches("^bl.*").buildSelectorJson();

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertEquals("^bl.*", selector.get("color").get("$regex").asText());
    }

    @Test
    void always_includes_docType_in_selector() throws JsonProcessingException {
      String json = registry.query(TestAsset.class).buildSelectorJson();

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertNotNull(selector.get("docType"));
    }
  }

  // ============================
  // Combined conditions
  // ============================

  @Nested
  class combined_conditions {

    @Test
    void combines_multiple_conditions_with_implicit_and() throws JsonProcessingException {
      String json =
          registry
              .query(TestAsset.class)
              .where("color")
              .is("blue")
              .and("size")
              .greaterThan(10)
              .and("owner")
              .in("Alice", "Bob")
              .buildSelectorJson();

      JsonNode selector = MAPPER.readTree(json).get("selector");

      assertEquals("blue", selector.get("color").get("$eq").asText());
      assertEquals(10, selector.get("size").get("$gt").asInt());

      JsonNode inNode = selector.get("owner").get("$in");
      assertEquals(2, inNode.size());
    }

    @Test
    void full_api_example_from_challenge() throws JsonProcessingException {
      // This matches the exact API from the coding challenge description
      String json =
          registry
              .query(TestAsset.class)
              .where("color")
              .is("blue")
              .and("size")
              .greaterThan(10)
              .and("owner")
              .in("Alice", "Bob")
              .sortBy("value", SortOrder.DESC)
              .limit(50)
              .buildSelectorJson();

      JsonNode root = MAPPER.readTree(json);
      JsonNode selector = root.get("selector");

      // conditions
      assertEquals("blue", selector.get("color").get("$eq").asText());
      assertEquals(10, selector.get("size").get("$gt").asInt());
      assertEquals("Alice", selector.get("owner").get("$in").get(0).asText());

      // sort
      JsonNode sort = root.get("sort");
      assertNotNull(sort);
      assertTrue(sort.isArray());
      assertEquals("desc", sort.get(0).get("value").asText());

      // limit
      assertEquals(50, root.get("limit").asInt());
    }

    @Test
    void same_field_range_query_merges_operators() throws JsonProcessingException {
      // Regression: second condition on same field must not overwrite the first.
      // Expected: "size": {"$gt": 10, "$lt": 100}
      String json =
          registry
              .query(TestAsset.class)
              .where("size")
              .greaterThan(10)
              .and("size")
              .lessThan(100)
              .buildSelectorJson();

      JsonNode sizeNode = MAPPER.readTree(json).get("selector").get("size");
      assertNotNull(sizeNode, "\"size\" field must be present");
      assertNotNull(sizeNode.get("$gt"), "$gt must not be overwritten by $lt");
      assertNotNull(sizeNode.get("$lt"), "$lt must be present");
      assertEquals(10, sizeNode.get("$gt").asInt());
      assertEquals(100, sizeNode.get("$lt").asInt());
    }

    @Test
    void same_field_gte_lte_both_preserved() throws JsonProcessingException {
      String json =
          registry
              .query(TestAsset.class)
              .where("appraisedValue")
              .greaterThanOrEqual(100)
              .and("appraisedValue")
              .lessThanOrEqual(999)
              .buildSelectorJson();

      JsonNode node = MAPPER.readTree(json).get("selector").get("appraisedValue");
      assertEquals(100, node.get("$gte").asInt());
      assertEquals(999, node.get("$lte").asInt());
    }

    @Test
    void same_field_merge_does_not_affect_other_fields() throws JsonProcessingException {
      String json =
          registry
              .query(TestAsset.class)
              .where("color")
              .is("blue")
              .and("size")
              .greaterThan(10)
              .and("size")
              .lessThan(100)
              .buildSelectorJson();

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertEquals("blue", selector.get("color").get("$eq").asText());
      assertEquals(10, selector.get("size").get("$gt").asInt());
      assertEquals(100, selector.get("size").get("$lt").asInt());
    }
  }

  // ============================
  // Sort, limit, bookmark
  // ============================

  @Nested
  class sorting_and_pagination {

    @Test
    void generates_sort_clause() throws JsonProcessingException {
      String json =
          registry
              .query(TestAsset.class)
              .where("color")
              .is("blue")
              .sortBy("appraisedValue", SortOrder.DESC)
              .buildSelectorJson();

      JsonNode sort = MAPPER.readTree(json).get("sort");
      assertNotNull(sort);
      assertTrue(sort.isArray());
      assertEquals(1, sort.size());
      assertEquals("desc", sort.get(0).get("appraisedValue").asText());
    }

    @Test
    void supports_multiple_sort_fields() throws JsonProcessingException {
      String json =
          registry
              .query(TestAsset.class)
              .where("color")
              .is("blue")
              .sortBy("owner", SortOrder.ASC)
              .sortBy("size", SortOrder.DESC)
              .buildSelectorJson();

      JsonNode sort = MAPPER.readTree(json).get("sort");
      assertEquals(2, sort.size());
      assertEquals("asc", sort.get(0).get("owner").asText());
      assertEquals("desc", sort.get(1).get("size").asText());
    }

    @Test
    void omits_sort_when_none_specified() throws JsonProcessingException {
      String json = registry.query(TestAsset.class).where("color").is("blue").buildSelectorJson();

      assertNull(MAPPER.readTree(json).get("sort"));
    }

    @Test
    void generates_limit() throws JsonProcessingException {
      String json =
          registry.query(TestAsset.class).where("color").is("blue").limit(25).buildSelectorJson();

      assertEquals(25, MAPPER.readTree(json).get("limit").asInt());
    }

    @Test
    void omits_limit_when_not_set() throws JsonProcessingException {
      String json = registry.query(TestAsset.class).where("color").is("blue").buildSelectorJson();

      assertNull(MAPPER.readTree(json).get("limit"));
    }

    @Test
    void generates_bookmark() throws JsonProcessingException {
      String json =
          registry
              .query(TestAsset.class)
              .where("color")
              .is("blue")
              .bookmark("g1AAAABPeJzLYWBg")
              .buildSelectorJson();

      assertEquals("g1AAAABPeJzLYWBg", MAPPER.readTree(json).get("bookmark").asText());
    }

    @Test
    void rejects_non_positive_limit() {
      assertThrows(IllegalArgumentException.class, () -> registry.query(TestAsset.class).limit(0));

      assertThrows(IllegalArgumentException.class, () -> registry.query(TestAsset.class).limit(-5));
    }
  }

  // ============================
  // Validation & edge cases
  // ============================

  @Nested
  class validation {

    @Test
    void rejects_blank_field_name_in_where() {
      assertThrows(IllegalArgumentException.class, () -> registry.query(TestAsset.class).where(""));
    }

    @Test
    void rejects_null_field_name_in_where() {
      assertThrows(NullPointerException.class, () -> registry.query(TestAsset.class).where(null));
    }

    @Test
    void rejects_empty_in_values() {
      assertThrows(
          IllegalArgumentException.class,
          () -> registry.query(TestAsset.class).where("owner").in());
    }

    @Test
    void rejects_null_regex_pattern() {
      assertThrows(
          NullPointerException.class,
          () -> registry.query(TestAsset.class).where("color").matches(null));
    }

    @Test
    void builder_getConditions_returns_unmodifiable_list() {
      RichQueryBuilder<TestAsset> builder =
          registry.query(TestAsset.class).where("color").is("blue");
      List<Condition> conditions = builder.getConditions();
      assertThrows(UnsupportedOperationException.class, () -> conditions.clear());
    }

    @Test
    void builder_getLimit_returns_negative_one_when_unset() {
      RichQueryBuilder<TestAsset> builder = registry.query(TestAsset.class);
      assertEquals(-1, builder.getLimit());
    }
  }

  // ============================
  // Execution with mocked stub
  // ============================

  @Nested
  class execution {

    @Test
    void execute_returns_empty_list_when_no_results() {
      given(stub.getQueryResult(anyString())).willReturn(emptyQueryResults());

      List<TestAsset> results = registry.query(TestAsset.class).where("color").is("blue").execute();

      assertTrue(results.isEmpty());
      then(stub).should().getQueryResult(anyString());
    }

    @Test
    void execute_returns_deserialized_entities() {
      TestAsset asset = new TestAsset("asset1", "blue", 15, 500, "Alice");
      byte[] serialized = JSON.serialize(asset).getBytes(StandardCharsets.UTF_8);

      given(stub.getQueryResult(anyString())).willReturn(singleResultIterator("key1", serialized));

      List<TestAsset> results = registry.query(TestAsset.class).where("color").is("blue").execute();

      assertEquals(1, results.size());
      assertEquals("asset1", results.get(0).assetID());
      assertEquals("blue", results.get(0).color());
      assertEquals(15, results.get(0).size());
      assertEquals(500, results.get(0).appraisedValue());
      assertEquals("Alice", results.get(0).owner());
    }

    @Test
    void execute_returns_multiple_entities() {
      TestAsset asset1 = new TestAsset("a1", "blue", 10, 300, "Alice");
      TestAsset asset2 = new TestAsset("a2", "blue", 20, 700, "Bob");
      byte[] ser1 = JSON.serialize(asset1).getBytes(StandardCharsets.UTF_8);
      byte[] ser2 = JSON.serialize(asset2).getBytes(StandardCharsets.UTF_8);

      given(stub.getQueryResult(anyString()))
          .willReturn(
              multiResultIterator(List.of(new SimpleKV("k1", ser1), new SimpleKV("k2", ser2))));

      List<TestAsset> results =
          registry
              .query(TestAsset.class)
              .where("color")
              .is("blue")
              .sortBy("size", SortOrder.ASC)
              .execute();

      assertEquals(2, results.size());
      assertEquals("Alice", results.get(0).owner());
      assertEquals("Bob", results.get(1).owner());
    }

    @Test
    void execute_uses_pagination_api_when_limit_is_set() {
      given(stub.getQueryResultWithPagination(anyString(), anyInt(), anyString()))
          .willReturn(emptyPaginatedResults());

      registry.query(TestAsset.class).where("color").is("blue").limit(10).execute();

      then(stub)
          .should()
          .getQueryResultWithPagination(
              argThat(
                  json -> {
                    try {
                      JsonNode root = MAPPER.readTree(json);
                      return root.has("selector") && root.get("selector").has("color");
                    } catch (JsonProcessingException e) {
                      return false;
                    }
                  }),
              eq(10),
              eq(""));
    }

    @Test
    void execute_uses_pagination_api_when_bookmark_is_set() {
      given(stub.getQueryResultWithPagination(anyString(), anyInt(), anyString()))
          .willReturn(emptyPaginatedResults());

      registry.query(TestAsset.class).where("color").is("blue").bookmark("bm123").execute();

      then(stub)
          .should()
          .getQueryResultWithPagination(anyString(), eq(Integer.MAX_VALUE), eq("bm123"));
    }

    @Test
    void execute_uses_simple_api_when_no_pagination() {
      given(stub.getQueryResult(anyString())).willReturn(emptyQueryResults());

      registry.query(TestAsset.class).where("color").is("blue").execute();

      then(stub).should().getQueryResult(anyString());
    }

    @Test
    void execute_result_list_is_unmodifiable() {
      given(stub.getQueryResult(anyString())).willReturn(emptyQueryResults());

      List<TestAsset> results = registry.query(TestAsset.class).where("color").is("blue").execute();

      assertThrows(UnsupportedOperationException.class, () -> results.add(null));
    }
  }

  // ============================
  // Paginated execution
  // ============================

  @Nested
  class paginated_execution {

    @Test
    void executePaged_returns_query_page_with_results_and_metadata() {
      TestAsset asset = new TestAsset("a1", "blue", 10, 300, "Alice");
      byte[] serialized = JSON.serialize(asset).getBytes(StandardCharsets.UTF_8);

      given(stub.getQueryResultWithPagination(anyString(), anyInt(), anyString()))
          .willReturn(
              paginatedResultIterator(List.of(new SimpleKV("k1", serialized)), "nextBookmark", 1));

      QueryPage<TestAsset> page =
          registry.query(TestAsset.class).where("color").is("blue").limit(10).executePaged();

      assertEquals(1, page.results().size());
      assertEquals("a1", page.results().get(0).assetID());
      assertEquals("nextBookmark", page.bookmark());
      assertEquals(1, page.fetchedRecordsCount());
    }

    @Test
    void executePaged_returns_empty_page_when_no_results() {
      given(stub.getQueryResultWithPagination(anyString(), anyInt(), anyString()))
          .willReturn(paginatedResultIterator(Collections.emptyList(), "", 0));

      QueryPage<TestAsset> page =
          registry.query(TestAsset.class).where("color").is("blue").limit(10).executePaged();

      assertTrue(page.results().isEmpty());
      assertEquals("", page.bookmark());
      assertEquals(0, page.fetchedRecordsCount());
    }

    @Test
    void executePaged_result_list_is_unmodifiable() {
      given(stub.getQueryResultWithPagination(anyString(), anyInt(), anyString()))
          .willReturn(paginatedResultIterator(Collections.emptyList(), "", 0));

      QueryPage<TestAsset> page =
          registry.query(TestAsset.class).where("color").is("blue").executePaged();

      assertThrows(UnsupportedOperationException.class, () -> page.results().add(null));
    }
  }

  // ============================
  // Helper utilities
  // ============================

  private record SimpleKV(String key, byte[] value) {}

  private static QueryResultsIterator<KeyValue> emptyQueryResults() {
    return new QueryResultsIterator<>() {
      @Override
      public void close() {}

      @Override
      public @Nonnull Iterator<KeyValue> iterator() {
        return Collections.emptyIterator();
      }
    };
  }

  private static QueryResultsIteratorWithMetadata<KeyValue> emptyPaginatedResults() {
    return paginatedResultIterator(Collections.emptyList(), "", 0);
  }

  private static QueryResultsIterator<KeyValue> singleResultIterator(
      final String key, final byte[] value) {
    return multiResultIterator(List.of(new SimpleKV(key, value)));
  }

  private static QueryResultsIterator<KeyValue> multiResultIterator(final List<SimpleKV> entries) {
    return new QueryResultsIterator<>() {
      @Override
      public void close() {}

      @Override
      public @Nonnull Iterator<KeyValue> iterator() {
        return kvIterator(entries);
      }
    };
  }

  private static QueryResultsIteratorWithMetadata<KeyValue> paginatedResultIterator(
      final List<SimpleKV> entries, final String nextBookmark, final int fetchedCount) {
    return new QueryResultsIteratorWithMetadata<>() {
      @Override
      public QueryResponseMetadata getMetadata() {
        return QueryResponseMetadata.newBuilder()
            .setBookmark(nextBookmark)
            .setFetchedRecordsCount(fetchedCount)
            .build();
      }

      @Override
      public void close() {}

      @Override
      public @Nonnull Iterator<KeyValue> iterator() {
        return kvIterator(entries);
      }
    };
  }

  private static Iterator<KeyValue> kvIterator(final List<SimpleKV> entries) {
    final Iterator<SimpleKV> inner = entries.iterator();
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return inner.hasNext();
      }

      @Override
      public KeyValue next() {
        SimpleKV kv = inner.next();
        return new KeyValue() {
          @Override
          public String getKey() {
            return kv.key();
          }

          @Override
          public byte[] getValue() {
            return kv.value();
          }

          @Override
          public String getStringValue() {
            return new String(kv.value(), StandardCharsets.UTF_8);
          }
        };
      }
    };
  }
}
