/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CouchDbSelector} in isolation.
 *
 * <p>These tests exercise the JSON serializer directly, without going through the builder chain,
 * ensuring that each Mango selector feature is correctly generated.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class CouchDbSelectorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String DOC_TYPE = "TEST_ENTITY";

  // ============================
  // Basic selector structure
  // ============================

  @Nested
  class basic_selector_structure {

    @Test
    void empty_conditions_produces_docType_only_selector() throws JsonProcessingException {
      String json =
          CouchDbSelector.build(
              DOC_TYPE, Collections.emptyList(), Collections.emptyList(), -1, null);

      JsonNode root = MAPPER.readTree(json);
      JsonNode selector = root.get("selector");
      assertNotNull(selector);
      assertEquals(DOC_TYPE, selector.get("docType").asText());
      assertEquals(1, selector.size(), "selector should only contain docType");
    }

    @Test
    void selector_always_contains_docType() throws JsonProcessingException {
      List<Condition> conditions = List.of(new Condition("color", QueryOperator.EQ, "blue"));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertEquals(DOC_TYPE, selector.get("docType").asText());
    }
  }

  // ============================
  // Operator serialization
  // ============================

  @Nested
  class operator_serialization {

    @Test
    void serializes_eq_operator() throws JsonProcessingException {
      List<Condition> conditions = List.of(new Condition("color", QueryOperator.EQ, "blue"));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertEquals("blue", selector.get("color").get("$eq").asText());
    }

    @Test
    void serializes_ne_operator() throws JsonProcessingException {
      List<Condition> conditions = List.of(new Condition("color", QueryOperator.NE, "red"));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertEquals("red", selector.get("color").get("$ne").asText());
    }

    @Test
    void serializes_gt_operator_with_integer() throws JsonProcessingException {
      List<Condition> conditions = List.of(new Condition("size", QueryOperator.GT, 10));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertEquals(10, selector.get("size").get("$gt").asInt());
    }

    @Test
    void serializes_gte_operator() throws JsonProcessingException {
      List<Condition> conditions = List.of(new Condition("size", QueryOperator.GTE, 5));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertEquals(5, selector.get("size").get("$gte").asInt());
    }

    @Test
    void serializes_lt_operator() throws JsonProcessingException {
      List<Condition> conditions = List.of(new Condition("size", QueryOperator.LT, 100));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertEquals(100, selector.get("size").get("$lt").asInt());
    }

    @Test
    void serializes_lte_operator() throws JsonProcessingException {
      List<Condition> conditions = List.of(new Condition("size", QueryOperator.LTE, 999));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertEquals(999, selector.get("size").get("$lte").asInt());
    }

    @Test
    void serializes_in_operator_with_collection() throws JsonProcessingException {
      List<Condition> conditions =
          List.of(new Condition("owner", QueryOperator.IN, Arrays.asList("Alice", "Bob")));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      JsonNode inNode = MAPPER.readTree(json).get("selector").get("owner").get("$in");
      assertTrue(inNode.isArray());
      assertEquals(2, inNode.size());
      assertEquals("Alice", inNode.get(0).asText());
      assertEquals("Bob", inNode.get(1).asText());
    }

    @Test
    void serializes_nin_operator() throws JsonProcessingException {
      List<Condition> conditions =
          List.of(new Condition("owner", QueryOperator.NIN, Arrays.asList("Eve")));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      JsonNode ninNode = MAPPER.readTree(json).get("selector").get("owner").get("$nin");
      assertTrue(ninNode.isArray());
      assertEquals(1, ninNode.size());
      assertEquals("Eve", ninNode.get(0).asText());
    }

    @Test
    void serializes_exists_true() throws JsonProcessingException {
      List<Condition> conditions = List.of(new Condition("field", QueryOperator.EXISTS, true));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      assertTrue(MAPPER.readTree(json).get("selector").get("field").get("$exists").asBoolean());
    }

    @Test
    void serializes_exists_false() throws JsonProcessingException {
      List<Condition> conditions = List.of(new Condition("field", QueryOperator.EXISTS, false));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      assertFalse(MAPPER.readTree(json).get("selector").get("field").get("$exists").asBoolean());
    }

    @Test
    void serializes_regex_operator() throws JsonProcessingException {
      List<Condition> conditions = List.of(new Condition("name", QueryOperator.REGEX, "^A.*"));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      assertEquals(
          "^A.*", MAPPER.readTree(json).get("selector").get("name").get("$regex").asText());
    }
  }

  // ============================
  // Value type handling
  // ============================

  @Nested
  class value_type_handling {

    @Test
    void serializes_long_value() throws JsonProcessingException {
      List<Condition> conditions = List.of(new Condition("bigNum", QueryOperator.GT, 9999999999L));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      assertEquals(
          9999999999L, MAPPER.readTree(json).get("selector").get("bigNum").get("$gt").asLong());
    }

    @Test
    void serializes_double_value() throws JsonProcessingException {
      List<Condition> conditions = List.of(new Condition("price", QueryOperator.LTE, 99.99));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      assertEquals(
          99.99, MAPPER.readTree(json).get("selector").get("price").get("$lte").asDouble(), 0.001);
    }

    @Test
    void serializes_float_value() throws JsonProcessingException {
      List<Condition> conditions = List.of(new Condition("weight", QueryOperator.GT, 1.5f));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      assertNotNull(MAPPER.readTree(json).get("selector").get("weight").get("$gt"));
    }

    @Test
    void serializes_null_value() throws JsonProcessingException {
      List<Condition> conditions = List.of(new Condition("field", QueryOperator.EQ, null));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      assertTrue(MAPPER.readTree(json).get("selector").get("field").get("$eq").isNull());
    }

    @Test
    void serializes_unknown_type_via_toString() throws JsonProcessingException {
      // An object type not explicitly handled should fall back to toString()
      Object customValue =
          new Object() {
            @Override
            public String toString() {
              return "custom-value";
            }
          };
      List<Condition> conditions = List.of(new Condition("field", QueryOperator.EQ, customValue));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      assertEquals(
          "custom-value", MAPPER.readTree(json).get("selector").get("field").get("$eq").asText());
    }

    @Test
    void serializes_mixed_types_in_collection() throws JsonProcessingException {
      List<Condition> conditions =
          List.of(
              new Condition(
                  "field", QueryOperator.IN, Arrays.asList("text", 42, 3.14, true, null)));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      JsonNode arr = MAPPER.readTree(json).get("selector").get("field").get("$in");
      assertEquals(5, arr.size());
      assertEquals("text", arr.get(0).asText());
      assertEquals(42, arr.get(1).asInt());
      assertEquals(3.14, arr.get(2).asDouble(), 0.001);
      assertTrue(arr.get(3).asBoolean());
      assertTrue(arr.get(4).isNull());
    }
  }

  // ============================
  // Same-field operator merging
  // ============================

  @Nested
  class operator_merging {

    @Test
    void merges_gt_and_lt_on_same_field() throws JsonProcessingException {
      List<Condition> conditions =
          List.of(
              new Condition("size", QueryOperator.GT, 10),
              new Condition("size", QueryOperator.LT, 100));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      JsonNode sizeNode = MAPPER.readTree(json).get("selector").get("size");
      assertEquals(10, sizeNode.get("$gt").asInt());
      assertEquals(100, sizeNode.get("$lt").asInt());
    }

    @Test
    void merges_gte_and_lte_on_same_field() throws JsonProcessingException {
      List<Condition> conditions =
          List.of(
              new Condition("price", QueryOperator.GTE, 100),
              new Condition("price", QueryOperator.LTE, 500));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      JsonNode priceNode = MAPPER.readTree(json).get("selector").get("price");
      assertEquals(100, priceNode.get("$gte").asInt());
      assertEquals(500, priceNode.get("$lte").asInt());
    }

    @Test
    void merge_does_not_affect_other_fields() throws JsonProcessingException {
      List<Condition> conditions =
          List.of(
              new Condition("color", QueryOperator.EQ, "blue"),
              new Condition("size", QueryOperator.GT, 10),
              new Condition("size", QueryOperator.LT, 100));
      String json = CouchDbSelector.build(DOC_TYPE, conditions, Collections.emptyList(), -1, null);

      JsonNode selector = MAPPER.readTree(json).get("selector");
      assertEquals("blue", selector.get("color").get("$eq").asText());
      assertEquals(10, selector.get("size").get("$gt").asInt());
      assertEquals(100, selector.get("size").get("$lt").asInt());
    }
  }

  // ============================
  // Sort serialization
  // ============================

  @Nested
  class sort_serialization {

    @Test
    void serializes_single_sort_asc() throws JsonProcessingException {
      String json =
          CouchDbSelector.build(
              DOC_TYPE,
              Collections.emptyList(),
              List.of(new SortField("name", SortOrder.ASC)),
              -1,
              null);

      JsonNode sort = MAPPER.readTree(json).get("sort");
      assertNotNull(sort);
      assertTrue(sort.isArray());
      assertEquals(1, sort.size());
      assertEquals("asc", sort.get(0).get("name").asText());
    }

    @Test
    void serializes_single_sort_desc() throws JsonProcessingException {
      String json =
          CouchDbSelector.build(
              DOC_TYPE,
              Collections.emptyList(),
              List.of(new SortField("value", SortOrder.DESC)),
              -1,
              null);

      JsonNode sort = MAPPER.readTree(json).get("sort");
      assertEquals("desc", sort.get(0).get("value").asText());
    }

    @Test
    void serializes_multiple_sort_fields_in_order() throws JsonProcessingException {
      String json =
          CouchDbSelector.build(
              DOC_TYPE,
              Collections.emptyList(),
              List.of(new SortField("owner", SortOrder.ASC), new SortField("size", SortOrder.DESC)),
              -1,
              null);

      JsonNode sort = MAPPER.readTree(json).get("sort");
      assertEquals(2, sort.size());
      assertEquals("asc", sort.get(0).get("owner").asText());
      assertEquals("desc", sort.get(1).get("size").asText());
    }

    @Test
    void omits_sort_when_empty() throws JsonProcessingException {
      String json =
          CouchDbSelector.build(
              DOC_TYPE, Collections.emptyList(), Collections.emptyList(), -1, null);

      assertNull(MAPPER.readTree(json).get("sort"));
    }
  }

  // ============================
  // Limit and bookmark
  // ============================

  @Nested
  class limit_and_bookmark {

    @Test
    void includes_limit_when_positive() throws JsonProcessingException {
      String json =
          CouchDbSelector.build(
              DOC_TYPE, Collections.emptyList(), Collections.emptyList(), 25, null);

      assertEquals(25, MAPPER.readTree(json).get("limit").asInt());
    }

    @Test
    void omits_limit_when_negative() throws JsonProcessingException {
      String json =
          CouchDbSelector.build(
              DOC_TYPE, Collections.emptyList(), Collections.emptyList(), -1, null);

      assertNull(MAPPER.readTree(json).get("limit"));
    }

    @Test
    void omits_limit_when_zero() throws JsonProcessingException {
      String json =
          CouchDbSelector.build(
              DOC_TYPE, Collections.emptyList(), Collections.emptyList(), 0, null);

      assertNull(MAPPER.readTree(json).get("limit"));
    }

    @Test
    void includes_bookmark_when_present() throws JsonProcessingException {
      String json =
          CouchDbSelector.build(
              DOC_TYPE, Collections.emptyList(), Collections.emptyList(), -1, "g1AAAABPeJzLYWBg");

      assertEquals("g1AAAABPeJzLYWBg", MAPPER.readTree(json).get("bookmark").asText());
    }

    @Test
    void omits_bookmark_when_null() throws JsonProcessingException {
      String json =
          CouchDbSelector.build(
              DOC_TYPE, Collections.emptyList(), Collections.emptyList(), -1, null);

      assertNull(MAPPER.readTree(json).get("bookmark"));
    }

    @Test
    void omits_bookmark_when_blank() throws JsonProcessingException {
      String json =
          CouchDbSelector.build(
              DOC_TYPE, Collections.emptyList(), Collections.emptyList(), -1, "   ");

      assertNull(MAPPER.readTree(json).get("bookmark"));
    }

    @Test
    void includes_both_limit_and_bookmark() throws JsonProcessingException {
      String json =
          CouchDbSelector.build(
              DOC_TYPE, Collections.emptyList(), Collections.emptyList(), 10, "abc123");

      JsonNode root = MAPPER.readTree(json);
      assertEquals(10, root.get("limit").asInt());
      assertEquals("abc123", root.get("bookmark").asText());
    }
  }

  // ============================
  // Full selector composition
  // ============================

  @Nested
  class full_composition {

    @Test
    void builds_complete_selector_with_all_features() throws JsonProcessingException {
      List<Condition> conditions =
          List.of(
              new Condition("color", QueryOperator.EQ, "blue"),
              new Condition("size", QueryOperator.GT, 10),
              new Condition("owner", QueryOperator.IN, Arrays.asList("Alice", "Bob")));
      List<SortField> sorts = List.of(new SortField("value", SortOrder.DESC));

      String json = CouchDbSelector.build(DOC_TYPE, conditions, sorts, 50, null);

      JsonNode root = MAPPER.readTree(json);
      JsonNode selector = root.get("selector");

      // docType
      assertEquals(DOC_TYPE, selector.get("docType").asText());

      // conditions
      assertEquals("blue", selector.get("color").get("$eq").asText());
      assertEquals(10, selector.get("size").get("$gt").asInt());
      assertEquals("Alice", selector.get("owner").get("$in").get(0).asText());
      assertEquals("Bob", selector.get("owner").get("$in").get(1).asText());

      // sort
      JsonNode sort = root.get("sort");
      assertEquals(1, sort.size());
      assertEquals("desc", sort.get(0).get("value").asText());

      // limit
      assertEquals(50, root.get("limit").asInt());
    }

    @Test
    void output_is_valid_json() {
      List<Condition> conditions =
          List.of(
              new Condition("a", QueryOperator.EQ, "x"), new Condition("b", QueryOperator.GT, 1));

      String json =
          CouchDbSelector.build(
              DOC_TYPE, conditions, List.of(new SortField("a", SortOrder.ASC)), 10, "bm");

      assertDoesNotThrow(() -> MAPPER.readTree(json), "Output must be valid JSON");
    }
  }
}
