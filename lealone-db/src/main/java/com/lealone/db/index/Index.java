/*
 * Copyright 2004-2014 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package com.lealone.db.index;

import com.lealone.common.exceptions.DbException;
import com.lealone.db.async.AsyncResultHandler;
import com.lealone.db.result.SortOrder;
import com.lealone.db.row.Row;
import com.lealone.db.row.SearchRow;
import com.lealone.db.schema.SchemaObject;
import com.lealone.db.session.ServerSession;
import com.lealone.db.table.Column;
import com.lealone.db.table.Table;
import com.lealone.db.value.Value;
import com.lealone.storage.CursorParameters;

/**
 * An index. Indexes are used to speed up searching data.
 * 
 * @author H2 Group
 * @author zhh
 */
public interface Index extends SchemaObject {

    /**
     * Get the table on which this index is based.
     *
     * @return the table
     */
    Table getTable();

    /**
     * Get the index type.
     *
     * @return the index type
     */
    IndexType getIndexType();

    /**
     * Get the indexed columns as index columns (with ordering information).
     *
     * @return the index columns
     */
    IndexColumn[] getIndexColumns();

    /**
     * Get the indexed columns.
     *
     * @return the columns
     */
    Column[] getColumns();

    /**
     * Get the indexed column ids.
     *
     * @return the column ids
     */
    int[] getColumnIds();

    /**
     * Get the index of a column in the list of index columns
     *
     * @param col the column
     * @return the index (0 meaning first column)
     */
    int getColumnIndex(Column col);

    /**
     * Get the message to show in a EXPLAIN statement.
     *
     * @return the plan
     */
    String getPlanSQL();

    /**
     * Add a row to the index.
     *
     * @param session the session to use
     * @param row the row to add
     */
    default void add(ServerSession session, Row row, AsyncResultHandler<Integer> handler) {
        throw DbException.getUnsupportedException("add row");
    }

    default void update(ServerSession session, Row oldRow, Row newRow, Value[] oldColumns,
            int[] updateColumns, boolean isLockedBySelf, AsyncResultHandler<Integer> handler) {
        remove(session, oldRow, oldColumns, isLockedBySelf, ar1 -> {
            if (ar1.isSucceeded()) {
                add(session, newRow, ar2 -> {
                    handler.handle(ar2);
                });
            } else {
                handler.handle(ar1);
            }
        });
    }

    /**
     * Remove a row from the index.
     *
     * @param session the session
     * @param row the row
     */
    default void remove(ServerSession session, Row row, Value[] oldColumns, boolean isLockedBySelf,
            AsyncResultHandler<Integer> handler) {
        throw DbException.getUnsupportedException("remove row");
    }

    /**
     * Find a row or a list of rows and create a cursor to iterate over the result.
     *
     * @param session the session
     * @param first the first row, or null for no limit
     * @param last the last row, or null for no limit
     * @return the cursor to iterate over the results
     */
    Cursor find(ServerSession session, SearchRow first, SearchRow last);

    Cursor find(ServerSession session, CursorParameters<SearchRow> parameters);

    /**
     * Check if the index can directly look up the lowest or highest value of a
     * column.
     *
     * @return true if it can
     */
    boolean canGetFirstOrLast();

    /**
     * Find the first (or last) value of this index.
     *
     * @param session the session
     * @param first true if the first (lowest for ascending indexes) or last
     *            value should be returned
     * @return a SearchRow or null
     */
    SearchRow findFirstOrLast(ServerSession session, boolean first);

    /**
     * Check if the index supports distinct query.
     *
     * @return true if it supports
     */
    boolean supportsDistinctQuery();

    /**
     * Find a distinct list of rows and create a cursor to iterate over the result.
     *
     * @param session the session
     * @return the cursor to iterate over the results
     */
    Cursor findDistinct(ServerSession session);

    /**
     * Can this index iterate over all rows?
     *
     * @return true if it can
     */
    boolean canScan();

    /**
     * Does this index support lookup by row id?
     *
     * @return true if it does
     */
    boolean isRowIdIndex();

    /**
     * Get the row with the given key.
     *
     * @param session the session
     * @param key the unique key
     * @return the row
     */
    Row getRow(ServerSession session, long key);

    /**
     * Compare two rows.
     *
     * @param rowData the first row
     * @param compare the second row
     * @return 0 if both rows are equal, -1 if the first row is smaller, otherwise 1
     */
    int compareRows(SearchRow rowData, SearchRow compare);

    /**
     * Estimate the cost to search for rows given the search mask.
     * There is one element per column in the search mask.
     * For possible search masks, see IndexCondition.
     *
     * @param session the session
     * @param masks per-column comparison bit masks, null means 'always false',
     *              see constants in IndexCondition
     * @param filter the table filter
     * @param sortOrder the sort order
     * @return the estimated cost
     */
    double getCost(ServerSession session, int[] masks, SortOrder sortOrder);

    default void setLastIndexedRowKey(Long rowKey) {
    }

    default Long getLastIndexedRowKey() {
        return null;
    }

    default void setBuilding(boolean building) {
    }

    default boolean isBuilding() {
        return false;
    }

    default boolean isClosed() {
        return false;
    }

    /**
     * Close this index.
     *
     * @param session the session used to write data
     */
    void close(ServerSession session);

    /**
     * Remove the index.
     *
     * @param session the session
     */
    void remove(ServerSession session);

    /**
     * Remove all rows from the index.
     *
     * @param session the session
     */
    void truncate(ServerSession session);

    /**
     * Get the used disk space for this index.
     *
     * @return the estimated number of bytes
     */
    long getDiskSpaceUsed();

    /**
     * Get the used memory space for this index.
     *
     * @return the estimated number of bytes
     */
    long getMemorySpaceUsed();

    /**
     * Check if the index needs to be rebuilt.
     * This method is called after opening an index.
     *
     * @return true if a rebuild is required.
     */
    boolean needRebuild();

    IndexOperator getIndexOperator();

    void setIndexOperator(IndexOperator indexOperator);
}
