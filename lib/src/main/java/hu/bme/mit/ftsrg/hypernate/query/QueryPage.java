/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import java.util.List;

/**
 * Holds the results of a paginated rich query along with pagination metadata.
 *
 * <p>Instances are returned by {@link RichQueryBuilder#executePaged()} and provide the data
 * necessary for cursor-based pagination through large result sets.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * QueryPage<Asset> page1 = registry.query(Asset.class)
 *     .where("color").is("blue")
 *     .limit(10)
 *     .executePaged();
 *
 * List<Asset> items = page1.results();
 * String nextBookmark = page1.bookmark();
 *
 * // fetch page 2
 * QueryPage<Asset> page2 = registry.query(Asset.class)
 *     .where("color").is("blue")
 *     .limit(10)
 *     .bookmark(nextBookmark)
 *     .executePaged();
 * }</pre>
 *
 * @param results the list of deserialized entities for this page (never {@code null})
 * @param bookmark the bookmark string for fetching the next page; empty when there are no more
 *     results
 * @param fetchedRecordsCount the number of records fetched as reported by Fabric
 * @param <T> the entity type
 */
public record QueryPage<T>(List<T> results, String bookmark, int fetchedRecordsCount) {}
