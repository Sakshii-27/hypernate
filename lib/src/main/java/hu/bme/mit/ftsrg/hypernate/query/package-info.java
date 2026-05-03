/* SPDX-License-Identifier: Apache-2.0 */

/**
 * Fluent rich query builder for CouchDB Mango selectors in Hyperledger Fabric.
 *
 * <p>This package provides a composable, type-safe API for constructing and executing CouchDB rich
 * queries against the Fabric world state. The entry point is {@link
 * hu.bme.mit.ftsrg.hypernate.registry.Registry#query(Class)}, which returns a {@link
 * hu.bme.mit.ftsrg.hypernate.query.RichQueryBuilder}.
 *
 * <h2>Quick Example</h2>
 *
 * <pre>{@code
 * List<Asset> results = registry.query(Asset.class)
 *     .where("color").is("blue")
 *     .and("size").greaterThan(10)
 *     .sortBy("value", SortOrder.DESC)
 *     .limit(50)
 *     .execute();
 * }</pre>
 *
 * <h2>Key Classes</h2>
 *
 * <ul>
 *   <li>{@link hu.bme.mit.ftsrg.hypernate.query.RichQueryBuilder} — the main builder
 *   <li>{@link hu.bme.mit.ftsrg.hypernate.query.FieldCondition} — intermediate step for field
 *       comparisons
 *   <li>{@link hu.bme.mit.ftsrg.hypernate.query.SortOrder} — ascending / descending sort
 *   <li>{@link hu.bme.mit.ftsrg.hypernate.query.QueryPage} — paginated result wrapper
 *   <li>{@link hu.bme.mit.ftsrg.hypernate.query.RichQueryException} — query-related errors
 * </ul>
 *
 * <p><b>Note:</b> Rich queries require CouchDB as the Fabric state database.
 *
 * @see hu.bme.mit.ftsrg.hypernate.registry.Registry#query(Class)
 */
package hu.bme.mit.ftsrg.hypernate.query;
