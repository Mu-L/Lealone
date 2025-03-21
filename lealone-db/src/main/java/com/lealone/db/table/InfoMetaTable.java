/*
 * Copyright 2004-2014 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package com.lealone.db.table;

import java.io.Reader;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.lealone.common.exceptions.DbException;
import com.lealone.common.util.MathUtils;
import com.lealone.common.util.StatementBuilder;
import com.lealone.common.util.StringUtils;
import com.lealone.common.util.Utils;
import com.lealone.db.Constants;
import com.lealone.db.Database;
import com.lealone.db.DbObject;
import com.lealone.db.DbObjectType;
import com.lealone.db.LealoneDatabase;
import com.lealone.db.SysProperties;
import com.lealone.db.auth.Right;
import com.lealone.db.auth.Role;
import com.lealone.db.auth.User;
import com.lealone.db.command.Command;
import com.lealone.db.constraint.Constraint;
import com.lealone.db.constraint.ConstraintCheck;
import com.lealone.db.constraint.ConstraintReferential;
import com.lealone.db.constraint.ConstraintUnique;
import com.lealone.db.index.Cursor;
import com.lealone.db.index.Index;
import com.lealone.db.index.IndexColumn;
import com.lealone.db.lock.Lock;
import com.lealone.db.plugin.Plugin;
import com.lealone.db.plugin.PluginManager;
import com.lealone.db.plugin.PluginObject;
import com.lealone.db.result.SortOrder;
import com.lealone.db.row.Row;
import com.lealone.db.row.SearchRow;
import com.lealone.db.schema.Constant;
import com.lealone.db.schema.FunctionAlias;
import com.lealone.db.schema.Schema;
import com.lealone.db.schema.SchemaObject;
import com.lealone.db.schema.Sequence;
import com.lealone.db.schema.TriggerObject;
import com.lealone.db.schema.UserAggregate;
import com.lealone.db.schema.UserDataType;
import com.lealone.db.service.Service;
import com.lealone.db.session.ServerSession;
import com.lealone.db.util.Csv;
import com.lealone.db.value.CompareMode;
import com.lealone.db.value.DataType;
import com.lealone.db.value.Value;
import com.lealone.db.value.ValueString;
import com.lealone.db.value.ValueStringIgnoreCase;
import com.lealone.server.ProtocolServerEngine;
import com.lealone.sql.SQLEngine;
import com.lealone.storage.StorageEngine;
import com.lealone.transaction.TransactionEngine;

/**
 * This class is responsible to build the database information meta data pseudo tables.
 *
 * @author H2 Group
 * @author zhh
 */
public class InfoMetaTable extends MetaTable {

    private static final String CHARACTER_SET_NAME = "Unicode";

    private static final int TABLES = 0;
    private static final int COLUMNS = 1;
    private static final int INDEXES = 2;
    private static final int TABLE_TYPES = 3;
    private static final int TYPE_INFO = 4;
    private static final int CATALOGS = 5;
    private static final int SETTINGS = 6;
    private static final int HELP = 7;
    private static final int SEQUENCES = 8;
    private static final int USERS = 9;
    private static final int ROLES = 10;
    private static final int RIGHTS = 11;
    private static final int FUNCTION_ALIASES = 12;
    private static final int SCHEMAS = 13;
    private static final int TABLE_PRIVILEGES = 14;
    private static final int COLUMN_PRIVILEGES = 15;
    private static final int COLLATIONS = 16;
    private static final int VIEWS = 17;
    private static final int CROSS_REFERENCES = 18;
    private static final int CONSTRAINTS = 19;
    private static final int FUNCTION_COLUMNS = 20;
    private static final int CONSTANTS = 21;
    private static final int DOMAINS = 22;
    private static final int TRIGGERS = 23;
    private static final int SESSIONS = 24;
    private static final int LOCKS = 25;
    private static final int SESSION_STATE = 26;
    private static final int DATABASES = 27;
    private static final int DB_OBJECTS = 28; // 对应SYS表
    private static final int SERVICES = 29;
    private static final int PLUGINS = 30;

    /**
     * Get the number of meta table types. Supported meta table
     * types are 0 .. this value - 1.
     *
     * @return the number of meta table types
     */
    public static int getMetaTableTypeCount() {
        return PLUGINS + 1;
    }

    public InfoMetaTable(Schema schema, int id, int type) {
        super(schema, id, type);
    }

    @Override
    public String createColumns() {
        Column[] cols;
        String indexColumnName = null;
        switch (type) {
        case TABLES:
            setObjectName("TABLES");
            cols = createColumns("TABLE_CATALOG", "TABLE_SCHEMA", "TABLE_NAME", "TABLE_TYPE",
                    // extensions
                    "STORAGE_TYPE", "SQL", "REMARKS", "LAST_MODIFICATION BIGINT", "ID INT", "TYPE_NAME",
                    "TABLE_CLASS");
            indexColumnName = "TABLE_NAME";
            break;
        case COLUMNS:
            setObjectName("COLUMNS");
            cols = createColumns("TABLE_CATALOG", "TABLE_SCHEMA", "TABLE_NAME", "COLUMN_NAME",
                    "ORDINAL_POSITION INT", "COLUMN_DEFAULT", "IS_NULLABLE", "DATA_TYPE INT",
                    "CHARACTER_MAXIMUM_LENGTH INT", "CHARACTER_OCTET_LENGTH INT",
                    "NUMERIC_PRECISION INT", "NUMERIC_PRECISION_RADIX INT", "NUMERIC_SCALE INT",
                    "CHARACTER_SET_NAME", "COLLATION_NAME",
                    // extensions
                    "TYPE_NAME", "NULLABLE INT", "IS_COMPUTED BIT", "SELECTIVITY INT",
                    "CHECK_CONSTRAINT", "SEQUENCE_NAME", "REMARKS", "SOURCE_DATA_TYPE SMALLINT");
            indexColumnName = "TABLE_NAME";
            break;
        case INDEXES:
            setObjectName("INDEXES");
            cols = createColumns("TABLE_CATALOG", "TABLE_SCHEMA", "TABLE_NAME", "NON_UNIQUE BIT",
                    "INDEX_NAME", "ORDINAL_POSITION SMALLINT", "COLUMN_NAME", "CARDINALITY INT",
                    "PRIMARY_KEY BIT", "INDEX_TYPE_NAME", "IS_GENERATED BIT", "INDEX_TYPE SMALLINT",
                    "ASC_OR_DESC", "PAGES INT", "FILTER_CONDITION", "REMARKS", "SQL", "ID INT",
                    "SORT_TYPE INT", "CONSTRAINT_NAME", "INDEX_CLASS", "TABLE_ROW_COUNT",
                    "LAST_INDEXED_ROW_KEY");
            indexColumnName = "TABLE_NAME";
            break;
        case TABLE_TYPES:
            setObjectName("TABLE_TYPES");
            cols = createColumns("TYPE");
            break;
        case TYPE_INFO:
            setObjectName("TYPE_INFO");
            cols = createColumns("TYPE_NAME", "DATA_TYPE INT", "PRECISION INT", "PREFIX", "SUFFIX",
                    "PARAMS", "AUTO_INCREMENT BIT", "MINIMUM_SCALE SMALLINT", "MAXIMUM_SCALE SMALLINT",
                    "RADIX INT", "POS INT", "CASE_SENSITIVE BIT", "NULLABLE SMALLINT",
                    "SEARCHABLE SMALLINT");
            break;
        case CATALOGS:
            setObjectName("CATALOGS");
            cols = createColumns("CATALOG_NAME");
            break;
        case SETTINGS:
            setObjectName("SETTINGS");
            cols = createColumns("NAME", "SCOPE", "VALUE");
            break;
        case HELP:
            setObjectName("HELP");
            cols = createColumns("ID INT", "SECTION", "TOPIC", "SYNTAX", "TEXT", "EXAMPLE");
            break;
        case SEQUENCES:
            setObjectName("SEQUENCES");
            cols = createColumns("SEQUENCE_CATALOG", "SEQUENCE_SCHEMA", "SEQUENCE_NAME",
                    "CURRENT_VALUE BIGINT", "INCREMENT BIGINT", "IS_GENERATED BIT", "REMARKS",
                    "CACHE BIGINT", "ID INT");
            break;
        case USERS:
            setObjectName("USERS");
            cols = createColumns("NAME", "ADMIN", "REMARKS", "ID INT");
            break;
        case ROLES:
            setObjectName("ROLES");
            cols = createColumns("NAME", "REMARKS", "ID INT");
            break;
        case RIGHTS:
            setObjectName("RIGHTS");
            cols = createColumns("GRANTEE", "GRANTEETYPE", "GRANTEDROLE", "RIGHTS", "TABLE_SCHEMA",
                    "TABLE_NAME", "ID INT");
            indexColumnName = "TABLE_NAME";
            break;
        case FUNCTION_ALIASES:
            setObjectName("FUNCTION_ALIASES");
            cols = createColumns("ALIAS_CATALOG", "ALIAS_SCHEMA", "ALIAS_NAME", "JAVA_CLASS",
                    "JAVA_METHOD", "DATA_TYPE INT", "TYPE_NAME", "COLUMN_COUNT INT",
                    "RETURNS_RESULT SMALLINT", "REMARKS", "ID INT", "SOURCE");
            break;
        case FUNCTION_COLUMNS:
            setObjectName("FUNCTION_COLUMNS");
            cols = createColumns("ALIAS_CATALOG", "ALIAS_SCHEMA", "ALIAS_NAME", "JAVA_CLASS",
                    "JAVA_METHOD", "COLUMN_COUNT INT", "POS INT", "COLUMN_NAME", "DATA_TYPE INT",
                    "TYPE_NAME", "PRECISION INT", "SCALE SMALLINT", "RADIX SMALLINT",
                    "NULLABLE SMALLINT", "COLUMN_TYPE SMALLINT", "REMARKS", "COLUMN_DEFAULT");
            break;
        case SCHEMAS:
            setObjectName("SCHEMAS");
            cols = createColumns("CATALOG_NAME", "SCHEMA_NAME", "SCHEMA_OWNER",
                    "DEFAULT_CHARACTER_SET_NAME", "DEFAULT_COLLATION_NAME", "IS_DEFAULT BIT", "REMARKS",
                    "ID INT");
            break;
        case TABLE_PRIVILEGES:
            setObjectName("TABLE_PRIVILEGES");
            cols = createColumns("GRANTOR", "GRANTEE", "TABLE_CATALOG", "TABLE_SCHEMA", "TABLE_NAME",
                    "PRIVILEGE_TYPE", "IS_GRANTABLE");
            indexColumnName = "TABLE_NAME";
            break;
        case COLUMN_PRIVILEGES:
            setObjectName("COLUMN_PRIVILEGES");
            cols = createColumns("GRANTOR", "GRANTEE", "TABLE_CATALOG", "TABLE_SCHEMA", "TABLE_NAME",
                    "COLUMN_NAME", "PRIVILEGE_TYPE", "IS_GRANTABLE");
            indexColumnName = "TABLE_NAME";
            break;
        case COLLATIONS:
            setObjectName("COLLATIONS");
            cols = createColumns("NAME", "KEY");
            break;
        case VIEWS:
            setObjectName("VIEWS");
            cols = createColumns("TABLE_CATALOG", "TABLE_SCHEMA", "TABLE_NAME", "VIEW_DEFINITION",
                    "CHECK_OPTION", "IS_UPDATABLE", "STATUS", "REMARKS", "ID INT");
            indexColumnName = "TABLE_NAME";
            break;
        case CROSS_REFERENCES:
            setObjectName("CROSS_REFERENCES");
            cols = createColumns("PKTABLE_CATALOG", "PKTABLE_SCHEMA", "PKTABLE_NAME", "PKCOLUMN_NAME",
                    "FKTABLE_CATALOG", "FKTABLE_SCHEMA", "FKTABLE_NAME", "FKCOLUMN_NAME",
                    "ORDINAL_POSITION SMALLINT", "UPDATE_RULE SMALLINT", "DELETE_RULE SMALLINT",
                    "FK_NAME", "PK_NAME", "DEFERRABILITY SMALLINT");
            indexColumnName = "PKTABLE_NAME";
            break;
        case CONSTRAINTS:
            setObjectName("CONSTRAINTS");
            cols = createColumns("CONSTRAINT_CATALOG", "CONSTRAINT_SCHEMA", "CONSTRAINT_NAME",
                    "CONSTRAINT_TYPE", "TABLE_CATALOG", "TABLE_SCHEMA", "TABLE_NAME",
                    "UNIQUE_INDEX_NAME", "CHECK_EXPRESSION", "COLUMN_LIST", "REMARKS", "SQL", "ID INT");
            indexColumnName = "TABLE_NAME";
            break;
        case CONSTANTS:
            setObjectName("CONSTANTS");
            cols = createColumns("CONSTANT_CATALOG", "CONSTANT_SCHEMA", "CONSTANT_NAME", "DATA_TYPE INT",
                    "REMARKS", "SQL", "ID INT");
            break;
        case DOMAINS:
            setObjectName("DOMAINS");
            cols = createColumns("DOMAIN_CATALOG", "DOMAIN_SCHEMA", "DOMAIN_NAME", "COLUMN_DEFAULT",
                    "IS_NULLABLE", "DATA_TYPE INT", "PRECISION INT", "SCALE INT", "TYPE_NAME",
                    "SELECTIVITY INT", "CHECK_CONSTRAINT", "REMARKS", "SQL", "ID INT");
            break;
        case TRIGGERS:
            setObjectName("TRIGGERS");
            cols = createColumns("TRIGGER_CATALOG", "TRIGGER_SCHEMA", "TRIGGER_NAME", "TRIGGER_TYPE",
                    "TABLE_CATALOG", "TABLE_SCHEMA", "TABLE_NAME", "BEFORE BIT", "JAVA_CLASS",
                    "QUEUE_SIZE INT", "NO_WAIT BIT", "REMARKS", "SQL", "ID INT");
            break;
        case SESSIONS: {
            setObjectName("SESSIONS");
            cols = createColumns("ID INT", "USER_NAME", "SESSION_START", "STATEMENT", "STATEMENT_START");
            break;
        }
        case LOCKS: {
            setObjectName("LOCKS");
            cols = createColumns("SESSION_ID INT", "LOCK_TYPE");
            break;
        }
        case SESSION_STATE: {
            setObjectName("SESSION_STATE");
            cols = createColumns("KEY", "SQL");
            break;
        }
        case DATABASES:
            setObjectName("DATABASES");
            cols = createColumns("ID", "DATABASE_NAME", "RUN_MODE", "NODES");
            break;
        case DB_OBJECTS:
            setObjectName("DB_OBJECTS");
            cols = createColumns("ID", "TYPE", "SQL");
            break;
        case SERVICES:
            setObjectName("SERVICES");
            cols = createColumns("ID INT", "SERVICE_CATALOG", "SERVICE_SCHEMA", "SERVICET_NAME", "SQL");
            break;
        case PLUGINS:
            setObjectName("PLUGINS");
            cols = createColumns("ID INT", "PLUGIN_CATALOG", "PLUGIN_NAME", "STATE", "SQL");
            break;
        default:
            throw DbException.getInternalError("type=" + type);
        }
        setColumns(cols);
        return indexColumnName;
    }

    private String identifier(String s) {
        if (s != null) {
            if (database.getMode().lowerCaseIdentifiers) {
                s = StringUtils.toLowerEnglish(s);
            } else if (database.getSettings().databaseToUpper) {
                s = StringUtils.toUpperEnglish(s);
            }
        }
        return s;
    }

    private ArrayList<Table> getAllTables(ServerSession session) {
        ArrayList<Table> tables = database.getAllTablesAndViews(true);
        ArrayList<Table> tempTables = session.getLocalTempTables();
        tables.addAll(tempTables);
        return tables;
    }

    private boolean checkIndex(ServerSession session, String value, Value indexFrom, Value indexTo) {
        if (value == null || (indexFrom == null && indexTo == null)) {
            return true;
        }
        Database db = session.getDatabase();
        if (database.getMode().lowerCaseIdentifiers) {
            Value v = ValueStringIgnoreCase.get(value);
            if (indexFrom.equals(indexTo) && db.compare(v, indexFrom) != 0) {
                return false;
            }
        } else {
            Value v = ValueString.get(value);
            if (indexFrom != null && db.compare(v, indexFrom) < 0) {
                return false;
            }
            if (indexTo != null && db.compare(v, indexTo) > 0) {
                return false;
            }
        }
        return true;
    }

    private static String replaceNullWithEmpty(String s) {
        return s == null ? "" : s;
    }

    private boolean hideTable(Table table, ServerSession session) {
        return table.isHidden() && session != database.getSystemSession();
    }

    /**
     * Generate the data for the given metadata table using the given first and
     * last row filters.
     *
     * @param session the session
     * @param first the first row to return
     * @param last the last row to return
     * @return the generated rows
     */
    @Override
    public ArrayList<Row> generateRows(ServerSession session, SearchRow first, SearchRow last) {
        Value indexFrom = null, indexTo = null;

        if (indexColumn >= 0) {
            if (first != null) {
                indexFrom = first.getValue(indexColumn);
            }
            if (last != null) {
                indexTo = last.getValue(indexColumn);
            }
        }

        ArrayList<Row> rows = Utils.newSmallArrayList();
        String catalog = identifier(database.getShortName());
        boolean admin = session.getUser().isAdmin();
        switch (type) {
        case TABLES: {
            for (Table table : getAllTables(session)) {
                String tableName = identifier(table.getName());
                if (!checkIndex(session, tableName, indexFrom, indexTo)) {
                    continue;
                }
                if (hideTable(table, session)) {
                    continue;
                }
                String storageType;
                if (table.isTemporary()) {
                    if (table.isGlobalTemporary()) {
                        storageType = "GLOBAL TEMPORARY";
                    } else {
                        storageType = "LOCAL TEMPORARY";
                    }
                } else {
                    storageType = table.isPersistIndexes() ? "CACHED" : "MEMORY";
                }
                add(rows,
                        // TABLE_CATALOG
                        catalog,
                        // TABLE_SCHEMA
                        identifier(table.getSchema().getName()),
                        // TABLE_NAME
                        tableName,
                        // TABLE_TYPE
                        table.getTableType().toString(),
                        // STORAGE_TYPE
                        storageType,
                        // SQL
                        table.getCreateSQL(),
                        // REMARKS
                        replaceNullWithEmpty(table.getComment()),
                        // LAST_MODIFICATION
                        "" + table.getMaxDataModificationId(),
                        // ID
                        "" + table.getId(),
                        // TYPE_NAME
                        null,
                        // TABLE_CLASS
                        table.getClass().getName());
            }
            break;
        }
        case COLUMNS: {
            for (Table table : getAllTables(session)) {
                String tableName = identifier(table.getName());
                if (!checkIndex(session, tableName, indexFrom, indexTo)) {
                    continue;
                }
                if (hideTable(table, session)) {
                    continue;
                }
                Column[] cols = table.getColumns();
                String collation = database.getCompareMode().getName();
                for (int j = 0; j < cols.length; j++) {
                    Column c = cols[j];
                    Sequence sequence = c.getSequence();
                    add(rows,
                            // TABLE_CATALOG
                            catalog,
                            // TABLE_SCHEMA
                            identifier(table.getSchema().getName()),
                            // TABLE_NAME
                            tableName,
                            // COLUMN_NAME
                            identifier(c.getName()),
                            // ORDINAL_POSITION
                            String.valueOf(j + 1),
                            // COLUMN_DEFAULT
                            c.getDefaultSQL(),
                            // IS_NULLABLE
                            c.isNullable() ? "YES" : "NO",
                            // DATA_TYPE
                            "" + DataType.convertTypeToSQLType(c.getType()),
                            // CHARACTER_MAXIMUM_LENGTH
                            "" + c.getPrecisionAsInt(),
                            // CHARACTER_OCTET_LENGTH
                            "" + c.getPrecisionAsInt(),
                            // NUMERIC_PRECISION
                            "" + c.getPrecisionAsInt(),
                            // NUMERIC_PRECISION_RADIX
                            "10",
                            // NUMERIC_SCALE
                            "" + c.getScale(),
                            // CHARACTER_SET_NAME
                            CHARACTER_SET_NAME,
                            // COLLATION_NAME
                            collation,
                            // TYPE_NAME
                            identifier(DataType.getDataType(c.getType()).name),
                            // NULLABLE
                            "" + (c.isNullable() ? DatabaseMetaData.columnNullable
                                    : DatabaseMetaData.columnNoNulls),
                            // IS_COMPUTED
                            "" + (c.isComputed() ? "TRUE" : "FALSE"),
                            // SELECTIVITY
                            "" + (c.getSelectivity()),
                            // CHECK_CONSTRAINT
                            c.getCheckConstraintSQL(session, c.getName()),
                            // SEQUENCE_NAME
                            sequence == null ? null : sequence.getName(),
                            // REMARKS
                            replaceNullWithEmpty(c.getComment()),
                            // SOURCE_DATA_TYPE
                            null);
                }
            }
            break;
        }
        case INDEXES: {
            for (Table table : getAllTables(session)) {
                String tableName = identifier(table.getName());
                if (!checkIndex(session, tableName, indexFrom, indexTo)) {
                    continue;
                }
                if (hideTable(table, session)) {
                    continue;
                }
                ArrayList<Index> indexes = table.getIndexes();
                ArrayList<Constraint> constraints = table.getConstraints();
                String tableRowCount = table.canGetRowCount() ? table.getRowCount(session) + "" : "";
                for (int j = 0; indexes != null && j < indexes.size(); j++) {
                    Index index = indexes.get(j);
                    if (index.getCreateSQL() == null) {
                        continue;
                    }
                    String constraintName = null;
                    for (int k = 0; constraints != null && k < constraints.size(); k++) {
                        Constraint constraint = constraints.get(k);
                        if (constraint.usesIndex(index)) {
                            if (index.getIndexType().isPrimaryKey()) {
                                if (constraint.getConstraintType().equals(Constraint.PRIMARY_KEY)) {
                                    constraintName = constraint.getName();
                                }
                            } else {
                                constraintName = constraint.getName();
                            }
                        }
                    }
                    IndexColumn[] cols = index.getIndexColumns();
                    String indexClass = index.getClass().getName();
                    String lastIndexedRowKey = index.getLastIndexedRowKey() + "";
                    for (int k = 0; k < cols.length; k++) {
                        IndexColumn idxCol = cols[k];
                        Column column = idxCol.column;
                        add(rows,
                                // TABLE_CATALOG
                                catalog,
                                // TABLE_SCHEMA
                                identifier(table.getSchema().getName()),
                                // TABLE_NAME
                                tableName,
                                // NON_UNIQUE
                                index.getIndexType().isUnique() ? "FALSE" : "TRUE",
                                // INDEX_NAME
                                identifier(index.getName()),
                                // ORDINAL_POSITION
                                "" + (k + 1),
                                // COLUMN_NAME
                                identifier(column.getName()),
                                // CARDINALITY
                                "0",
                                // PRIMARY_KEY
                                index.getIndexType().isPrimaryKey() ? "TRUE" : "FALSE",
                                // INDEX_TYPE_NAME
                                index.getIndexType().getSQL(),
                                // IS_GENERATED
                                index.getIndexType().getBelongsToConstraint() ? "TRUE" : "FALSE",
                                // INDEX_TYPE
                                "" + DatabaseMetaData.tableIndexOther,
                                // ASC_OR_DESC
                                (idxCol.sortType & SortOrder.DESCENDING) != 0 ? "D" : "A",
                                // PAGES
                                "0",
                                // FILTER_CONDITION
                                "",
                                // REMARKS
                                replaceNullWithEmpty(index.getComment()),
                                // SQL
                                index.getCreateSQL(),
                                // ID
                                "" + index.getId(),
                                // SORT_TYPE
                                "" + idxCol.sortType,
                                // CONSTRAINT_NAME
                                constraintName,
                                // INDEX_CLASS
                                indexClass,
                                // TABLE_ROW_COUNT
                                tableRowCount,
                                // LAST_INDEXED_ROW_KEY
                                lastIndexedRowKey);
                    }
                }
            }
            break;
        }
        case TABLE_TYPES: {
            for (TableType type : TableType.values()) {
                add(rows, type.name());
            }
            break;
        }
        case CATALOGS: {
            add(rows, catalog);
            break;
        }
        case SETTINGS: {
            // system properties
            if (admin) {
                String[] settings = {
                        "java.runtime.version",
                        "java.vm.name",
                        "java.vendor",
                        "os.name",
                        "os.arch",
                        "os.version",
                        "sun.os.patch.level",
                        // "file.separator",
                        "path.separator",
                        // "line.separator",
                        "user.country",
                        "user.language",
                        "user.variant",
                        // "file.encoding"
                };
                for (String s : settings) {
                    add(rows, s, "property", Utils.getProperty(s, ""));
                }
            }

            // system settings
            add(rows, "BUILD_ID", "system", "" + Constants.BUILD_ID);
            add(rows, "VERSION_MAJOR", "system", "" + Constants.VERSION_MAJOR);
            add(rows, "VERSION_MINOR", "system", "" + Constants.VERSION_MINOR);
            add(rows, "VERSION", "system", "" + Constants.getFullVersion());
            if (admin) {
                for (Entry<String, String> e : SysProperties.getSettings().entrySet()) {
                    String v = e.getValue();
                    if (v.contains("\r") || v.contains("\n"))
                        v = v.replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n");
                    add(rows, e.getKey(), "system", v);
                }
            }

            // database settings
            TreeMap<String, String> s = new TreeMap<>(database.getSettings().getSettings());
            s.put("EXCLUSIVE", database.getExclusiveSession() == null ? "false" : "true");
            s.put("MODE", database.getMode().getName());
            s.put("MULTI_THREADED", "1");
            s.put("MVCC", database.isMultiVersion() ? "true" : "false");
            for (Map.Entry<String, String> e : database.getParameters().entrySet()) {
                s.put(identifier(e.getKey()), e.getValue());
            }
            for (Entry<String, String> e : s.entrySet()) {
                add(rows, e.getKey(), "database", e.getValue());
            }

            // session settings
            for (Entry<String, String> e : session.getSettings().entrySet()) {
                add(rows, e.getKey(), "session", e.getValue());
            }

            addPlugin(StorageEngine.class, rows);
            addPlugin(TransactionEngine.class, rows);
            addPlugin(SQLEngine.class, rows);
            addPlugin(ProtocolServerEngine.class, rows);
            break;
        }
        case TYPE_INFO: {
            for (DataType t : DataType.getTypes()) {
                if (t.hidden || t.sqlType == Value.NULL) {
                    continue;
                }
                add(rows,
                        // TYPE_NAME
                        t.name,
                        // DATA_TYPE
                        String.valueOf(t.sqlType),
                        // PRECISION
                        String.valueOf(MathUtils.convertLongToInt(t.maxPrecision)),
                        // PREFIX
                        t.prefix,
                        // SUFFIX
                        t.suffix,
                        // PARAMS
                        t.params,
                        // AUTO_INCREMENT
                        String.valueOf(t.autoIncrement),
                        // MINIMUM_SCALE
                        String.valueOf(t.minScale),
                        // MAXIMUM_SCALE
                        String.valueOf(t.maxScale),
                        // RADIX
                        t.decimal ? "10" : null,
                        // POS
                        String.valueOf(t.sqlTypePos),
                        // CASE_SENSITIVE
                        String.valueOf(t.caseSensitive),
                        // NULLABLE
                        "" + DatabaseMetaData.typeNullable,
                        // SEARCHABLE
                        "" + DatabaseMetaData.typeSearchable);
            }
            break;
        }
        case HELP: {
            String resource = Constants.RESOURCES_DIR + "help.csv";
            try {
                Reader reader = Utils.getResourceAsReader(resource);
                Csv csv = new Csv();
                csv.setLineCommentCharacter('#');
                ResultSet rs = csv.read(reader, null);
                for (int i = 0; rs.next(); i++) {
                    add(rows,
                            // ID
                            String.valueOf(i),
                            // SECTION
                            rs.getString(1).trim(),
                            // TOPIC
                            rs.getString(2).trim(),
                            // SYNTAX
                            rs.getString(3).trim(),
                            // TEXT
                            rs.getString(4).trim(),
                            // EXAMPLE
                            rs.getString(5).trim());
                }
            } catch (Exception e) {
                throw DbException.convert(e);
            }
            break;
        }
        case SEQUENCES: {
            for (SchemaObject obj : database.getAllSchemaObjects(DbObjectType.SEQUENCE)) {
                Sequence s = (Sequence) obj;
                add(rows,
                        // SEQUENCE_CATALOG
                        catalog,
                        // SEQUENCE_SCHEMA
                        identifier(s.getSchema().getName()),
                        // SEQUENCE_NAME
                        identifier(s.getName()),
                        // CURRENT_VALUE
                        String.valueOf(s.getCurrentValue(session)),
                        // INCREMENT
                        String.valueOf(s.getIncrement()),
                        // IS_GENERATED
                        s.getBelongsToTable() ? "TRUE" : "FALSE",
                        // REMARKS
                        replaceNullWithEmpty(s.getComment()),
                        // CACHE
                        String.valueOf(s.getCacheSize()),
                        // ID
                        "" + s.getId());
            }
            break;
        }
        case USERS: {
            for (User u : database.getAllUsers()) {
                if (admin || session.getUser() == u) {
                    add(rows,
                            // NAME
                            identifier(u.getName()),
                            // ADMIN
                            String.valueOf(u.isAdmin()),
                            // REMARKS
                            replaceNullWithEmpty(u.getComment()),
                            // ID
                            "" + u.getId());
                }
            }
            break;
        }
        case ROLES: {
            for (Role r : database.getAllRoles()) {
                if (admin || session.getUser().isRoleGranted(r)) {
                    add(rows,
                            // NAME
                            identifier(r.getName()),
                            // REMARKS
                            replaceNullWithEmpty(r.getComment()),
                            // ID
                            "" + r.getId());
                }
            }
            break;
        }
        case RIGHTS: {
            if (admin) {
                for (Right r : database.getAllRights()) {
                    Role role = r.getGrantedRole();
                    DbObject grantee = r.getGrantee();
                    String rightType = grantee.getType() == DbObjectType.USER ? "USER" : "ROLE";
                    if (role == null) {
                        DbObject object = r.getGrantedObject();
                        Schema schema = null;
                        Table table = null;
                        if (object != null) {
                            if (object instanceof Schema) {
                                schema = (Schema) object;
                            } else if (object instanceof Table) {
                                table = (Table) object;
                                schema = table.getSchema();
                            }
                        }
                        String tableName = (table != null) ? identifier(table.getName()) : "";
                        String schemaName = (schema != null) ? identifier(schema.getName()) : "";
                        if (!checkIndex(session, tableName, indexFrom, indexTo)) {
                            continue;
                        }
                        add(rows,
                                // GRANTEE
                                identifier(grantee.getName()),
                                // GRANTEETYPE
                                rightType,
                                // GRANTEDROLE
                                "",
                                // RIGHTS
                                r.getRights(),
                                // TABLE_SCHEMA
                                schemaName,
                                // TABLE_NAME
                                tableName,
                                // ID
                                "" + r.getId());
                    } else {
                        add(rows,
                                // GRANTEE
                                identifier(grantee.getName()),
                                // GRANTEETYPE
                                rightType,
                                // GRANTEDROLE
                                identifier(role.getName()),
                                // RIGHTS
                                "",
                                // TABLE_SCHEMA
                                "",
                                // TABLE_NAME
                                "",
                                // ID
                                "" + r.getId());
                    }
                }
            }
            break;
        }
        case FUNCTION_ALIASES: {
            for (SchemaObject aliasAsSchemaObject : database
                    .getAllSchemaObjects(DbObjectType.FUNCTION_ALIAS)) {
                FunctionAlias alias = (FunctionAlias) aliasAsSchemaObject;
                for (FunctionAlias.JavaMethod method : alias.getJavaMethods()) {
                    int returnsResult = method.getDataType() == Value.NULL
                            ? DatabaseMetaData.procedureNoResult
                            : DatabaseMetaData.procedureReturnsResult;
                    add(rows,
                            // ALIAS_CATALOG
                            catalog,
                            // ALIAS_SCHEMA
                            alias.getSchema().getName(),
                            // ALIAS_NAME
                            identifier(alias.getName()),
                            // JAVA_CLASS
                            alias.getJavaClassName(),
                            // JAVA_METHOD
                            alias.getJavaMethodName(),
                            // DATA_TYPE
                            "" + DataType.convertTypeToSQLType(method.getDataType()),
                            // TYPE_NAME
                            DataType.getDataType(method.getDataType()).name,
                            // COLUMN_COUNT INT
                            "" + method.getParameterCount(),
                            // RETURNS_RESULT SMALLINT
                            "" + returnsResult,
                            // REMARKS
                            replaceNullWithEmpty(alias.getComment()),
                            // ID
                            "" + alias.getId(),
                            // SOURCE
                            alias.getSource()
                    // when adding more columns, see also below
                    );
                }
            }
            for (SchemaObject schemaObject : database.getAllSchemaObjects(DbObjectType.AGGREGATE)) {
                UserAggregate agg = (UserAggregate) schemaObject;
                int returnsResult = DatabaseMetaData.procedureReturnsResult;
                add(rows,
                        // ALIAS_CATALOG
                        catalog,
                        // ALIAS_SCHEMA
                        schemaObject.getSchema().getName(),
                        // ALIAS_NAME
                        identifier(agg.getName()),
                        // JAVA_CLASS
                        agg.getJavaClassName(),
                        // JAVA_METHOD
                        "",
                        // DATA_TYPE
                        "" + DataType.convertTypeToSQLType(Value.NULL),
                        // TYPE_NAME
                        DataType.getDataType(Value.NULL).name,
                        // COLUMN_COUNT INT
                        "1",
                        // RETURNS_RESULT SMALLINT
                        "" + returnsResult,
                        // REMARKS
                        replaceNullWithEmpty(agg.getComment()),
                        // ID
                        "" + agg.getId(),
                        // SOURCE
                        ""
                // when adding more columns, see also below
                );
            }
            break;
        }
        case FUNCTION_COLUMNS: {
            for (SchemaObject aliasAsSchemaObject : database
                    .getAllSchemaObjects(DbObjectType.FUNCTION_ALIAS)) {
                FunctionAlias alias = (FunctionAlias) aliasAsSchemaObject;
                for (FunctionAlias.JavaMethod method : alias.getJavaMethods()) {
                    Class<?>[] columnList = method.getColumnClasses();
                    for (int k = 0; k < columnList.length; k++) {
                        if (method.hasConnectionParam() && k == 0) {
                            continue;
                        }
                        Class<?> clazz = columnList[k];
                        int dataType = DataType.getTypeFromClass(clazz);
                        DataType dt = DataType.getDataType(dataType);
                        int nullable = clazz.isPrimitive() ? DatabaseMetaData.columnNoNulls
                                : DatabaseMetaData.columnNullable;
                        add(rows,
                                // ALIAS_CATALOG
                                catalog,
                                // ALIAS_SCHEMA
                                alias.getSchema().getName(),
                                // ALIAS_NAME
                                identifier(alias.getName()),
                                // JAVA_CLASS
                                alias.getJavaClassName(),
                                // JAVA_METHOD
                                alias.getJavaMethodName(),
                                // COLUMN_COUNT
                                "" + method.getParameterCount(),
                                // POS INT
                                "" + (k + (method.hasConnectionParam() ? 0 : 1)),
                                // COLUMN_NAME
                                "P" + (k + 1),
                                // DATA_TYPE
                                "" + DataType.convertTypeToSQLType(dt.type),
                                // TYPE_NAME
                                dt.name,
                                // PRECISION INT
                                "" + MathUtils.convertLongToInt(dt.defaultPrecision),
                                // SCALE
                                "" + dt.defaultScale,
                                // RADIX
                                "10",
                                // NULLABLE SMALLINT
                                "" + nullable,
                                // COLUMN_TYPE
                                "" + DatabaseMetaData.procedureColumnIn,
                                // REMARKS
                                "",
                                // COLUMN_DEFAULT
                                null);
                    }
                }
            }
            break;
        }
        case SCHEMAS: {
            String collation = database.getCompareMode().getName();
            for (Schema schema : database.getAllSchemas()) {
                add(rows,
                        // CATALOG_NAME
                        catalog,
                        // SCHEMA_NAME
                        identifier(schema.getName()),
                        // SCHEMA_OWNER
                        identifier(schema.getOwner().getName()),
                        // DEFAULT_CHARACTER_SET_NAME
                        CHARACTER_SET_NAME,
                        // DEFAULT_COLLATION_NAME
                        collation,
                        // IS_DEFAULT
                        Constants.SCHEMA_MAIN.equals(schema.getName()) ? "TRUE" : "FALSE",
                        // REMARKS
                        replaceNullWithEmpty(schema.getComment()),
                        // ID
                        "" + schema.getId());
            }
            break;
        }
        case TABLE_PRIVILEGES: {
            for (Right r : database.getAllRights()) {
                DbObject object = r.getGrantedObject();
                if (!(object instanceof Table)) {
                    continue;
                }
                Table table = (Table) object;
                if (table == null || hideTable(table, session)) {
                    continue;
                }
                String tableName = identifier(table.getName());
                if (!checkIndex(session, tableName, indexFrom, indexTo)) {
                    continue;
                }
                addPrivileges(rows, r.getGrantee(), catalog, table, null, r.getRightMask());
            }
            break;
        }
        case COLUMN_PRIVILEGES: {
            for (Right r : database.getAllRights()) {
                DbObject object = r.getGrantedObject();
                if (!(object instanceof Table)) {
                    continue;
                }
                Table table = (Table) object;
                if (table == null || hideTable(table, session)) {
                    continue;
                }
                String tableName = identifier(table.getName());
                if (!checkIndex(session, tableName, indexFrom, indexTo)) {
                    continue;
                }
                DbObject grantee = r.getGrantee();
                int mask = r.getRightMask();
                for (Column column : table.getColumns()) {
                    addPrivileges(rows, grantee, catalog, table, column.getName(), mask);
                }
            }
            break;
        }
        case COLLATIONS: {
            for (Locale l : Collator.getAvailableLocales()) {
                add(rows,
                        // NAME
                        CompareMode.getName(l),
                        // KEY
                        l.toString());
            }
            break;
        }
        case VIEWS: {
            for (Table table : getAllTables(session)) {
                if (table.getTableType() != TableType.VIEW) {
                    continue;
                }
                String tableName = identifier(table.getName());
                if (!checkIndex(session, tableName, indexFrom, indexTo)) {
                    continue;
                }
                TableView view = (TableView) table;
                add(rows,
                        // TABLE_CATALOG
                        catalog,
                        // TABLE_SCHEMA
                        identifier(table.getSchema().getName()),
                        // TABLE_NAME
                        tableName,
                        // VIEW_DEFINITION
                        table.getCreateSQL(),
                        // CHECK_OPTION
                        "NONE",
                        // IS_UPDATABLE
                        "NO",
                        // STATUS
                        view.isInvalid() ? "INVALID" : "VALID",
                        // REMARKS
                        replaceNullWithEmpty(view.getComment()),
                        // ID
                        "" + view.getId());
            }
            break;
        }
        case CROSS_REFERENCES: {
            for (SchemaObject obj : database.getAllSchemaObjects(DbObjectType.CONSTRAINT)) {
                Constraint constraint = (Constraint) obj;
                if (!(constraint.getConstraintType().equals(Constraint.REFERENTIAL))) {
                    continue;
                }
                ConstraintReferential ref = (ConstraintReferential) constraint;
                IndexColumn[] cols = ref.getColumns();
                IndexColumn[] refCols = ref.getRefColumns();
                Table tab = ref.getTable();
                Table refTab = ref.getRefTable();
                String tableName = identifier(refTab.getName());
                if (!checkIndex(session, tableName, indexFrom, indexTo)) {
                    continue;
                }
                int update = getRefAction(ref.getUpdateAction());
                int delete = getRefAction(ref.getDeleteAction());
                for (int j = 0; j < cols.length; j++) {
                    add(rows,
                            // PKTABLE_CATALOG
                            catalog,
                            // PKTABLE_SCHEMA
                            identifier(refTab.getSchema().getName()),
                            // PKTABLE_NAME
                            identifier(refTab.getName()),
                            // PKCOLUMN_NAME
                            identifier(refCols[j].column.getName()),
                            // FKTABLE_CATALOG
                            catalog,
                            // FKTABLE_SCHEMA
                            identifier(tab.getSchema().getName()),
                            // FKTABLE_NAME
                            identifier(tab.getName()),
                            // FKCOLUMN_NAME
                            identifier(cols[j].column.getName()),
                            // ORDINAL_POSITION
                            String.valueOf(j + 1),
                            // UPDATE_RULE SMALLINT
                            String.valueOf(update),
                            // DELETE_RULE SMALLINT
                            String.valueOf(delete),
                            // FK_NAME
                            identifier(ref.getName()),
                            // PK_NAME
                            identifier(ref.getUniqueIndex().getName()),
                            // DEFERRABILITY
                            "" + DatabaseMetaData.importedKeyNotDeferrable);
                }
            }
            break;
        }
        case CONSTRAINTS: {
            for (SchemaObject obj : database.getAllSchemaObjects(DbObjectType.CONSTRAINT)) {
                Constraint constraint = (Constraint) obj;
                String constraintType = constraint.getConstraintType();
                String checkExpression = null;
                IndexColumn[] indexColumns = null;
                Table table = constraint.getTable();
                if (hideTable(table, session)) {
                    continue;
                }
                Index index = constraint.getUniqueIndex();
                String uniqueIndexName = null;
                if (index != null) {
                    uniqueIndexName = index.getName();
                }
                String tableName = identifier(table.getName());
                if (!checkIndex(session, tableName, indexFrom, indexTo)) {
                    continue;
                }
                if (constraintType.equals(Constraint.CHECK)) {
                    checkExpression = ((ConstraintCheck) constraint).getExpression().getSQL();
                } else if (constraintType.equals(Constraint.UNIQUE)
                        || constraintType.equals(Constraint.PRIMARY_KEY)) {
                    indexColumns = ((ConstraintUnique) constraint).getColumns();
                } else if (constraintType.equals(Constraint.REFERENTIAL)) {
                    indexColumns = ((ConstraintReferential) constraint).getColumns();
                }
                String columnList = null;
                if (indexColumns != null) {
                    StatementBuilder buff = new StatementBuilder();
                    for (IndexColumn col : indexColumns) {
                        buff.appendExceptFirst(",");
                        buff.append(col.column.getName());
                    }
                    columnList = buff.toString();
                }
                add(rows,
                        // CONSTRAINT_CATALOG
                        catalog,
                        // CONSTRAINT_SCHEMA
                        identifier(constraint.getSchema().getName()),
                        // CONSTRAINT_NAME
                        identifier(constraint.getName()),
                        // CONSTRAINT_TYPE
                        constraintType,
                        // TABLE_CATALOG
                        catalog,
                        // TABLE_SCHEMA
                        identifier(table.getSchema().getName()),
                        // TABLE_NAME
                        tableName,
                        // UNIQUE_INDEX_NAME
                        uniqueIndexName,
                        // CHECK_EXPRESSION
                        checkExpression,
                        // COLUMN_LIST
                        columnList,
                        // REMARKS
                        replaceNullWithEmpty(constraint.getComment()),
                        // SQL
                        constraint.getCreateSQL(),
                        // ID
                        "" + constraint.getId());
            }
            break;
        }
        case CONSTANTS: {
            for (SchemaObject obj : database.getAllSchemaObjects(DbObjectType.CONSTANT)) {
                Constant constant = (Constant) obj;
                Value value = constant.getValue();
                add(rows,
                        // CONSTANT_CATALOG
                        catalog,
                        // CONSTANT_SCHEMA
                        identifier(constant.getSchema().getName()),
                        // CONSTANT_NAME
                        identifier(constant.getName()),
                        // CONSTANT_TYPE
                        "" + DataType.convertTypeToSQLType(value.getType()),
                        // REMARKS
                        replaceNullWithEmpty(constant.getComment()),
                        // SQL
                        value.getSQL(),
                        // ID
                        "" + constant.getId());
            }
            break;
        }
        case DOMAINS: {
            for (SchemaObject schemaObject : database.getAllSchemaObjects(DbObjectType.AGGREGATE)) {
                UserDataType dt = (UserDataType) schemaObject;
                Column col = dt.getColumn();
                add(rows,
                        // DOMAIN_CATALOG
                        catalog,
                        // DOMAIN_SCHEMA
                        schemaObject.getSchema().getName(),
                        // DOMAIN_NAME
                        identifier(dt.getName()),
                        // COLUMN_DEFAULT
                        col.getDefaultSQL(),
                        // IS_NULLABLE
                        col.isNullable() ? "YES" : "NO",
                        // DATA_TYPE
                        "" + col.getDataType().sqlType,
                        // PRECISION INT
                        "" + col.getPrecisionAsInt(),
                        // SCALE INT
                        "" + col.getScale(),
                        // TYPE_NAME
                        col.getDataType().name,
                        // SELECTIVITY INT
                        "" + col.getSelectivity(),
                        // CHECK_CONSTRAINT
                        "" + col.getCheckConstraintSQL(session, "VALUE"),
                        // REMARKS
                        replaceNullWithEmpty(dt.getComment()),
                        // SQL
                        "" + dt.getCreateSQL(),
                        // ID
                        "" + dt.getId());
            }
            break;
        }
        case TRIGGERS: {
            for (SchemaObject obj : database.getAllSchemaObjects(DbObjectType.TRIGGER)) {
                TriggerObject trigger = (TriggerObject) obj;
                Table table = trigger.getTable();
                add(rows,
                        // TRIGGER_CATALOG
                        catalog,
                        // TRIGGER_SCHEMA
                        identifier(trigger.getSchema().getName()),
                        // TRIGGER_NAME
                        identifier(trigger.getName()),
                        // TRIGGER_TYPE
                        trigger.getTypeNameList(),
                        // TABLE_CATALOG
                        catalog,
                        // TABLE_SCHEMA
                        identifier(table.getSchema().getName()),
                        // TABLE_NAME
                        identifier(table.getName()),
                        // BEFORE BIT
                        "" + trigger.isBefore(),
                        // JAVA_CLASS
                        trigger.getTriggerClassName(),
                        // QUEUE_SIZE INT
                        "" + trigger.getQueueSize(),
                        // NO_WAIT BIT
                        "" + trigger.isNoWait(),
                        // REMARKS
                        replaceNullWithEmpty(trigger.getComment()),
                        // SQL
                        trigger.getCreateSQL(),
                        // ID
                        "" + trigger.getId());
            }
            break;
        }
        case SESSIONS: {
            long now = System.currentTimeMillis();
            for (ServerSession s : database.getSessions(false)) {
                if (admin || s == session) {
                    Command command = s.getCurrentCommand();
                    long start = s.getCurrentCommandStart();
                    if (start == 0) {
                        start = now;
                    }
                    add(rows,
                            // ID
                            "" + s.getId(),
                            // USER_NAME
                            s.getUser().getName(),
                            // SESSION_START
                            new Timestamp(s.getSessionStart()).toString(),
                            // STATEMENT
                            command == null ? null : command.toString(),
                            // STATEMENT_START
                            new Timestamp(start).toString());
                }
            }
            break;
        }
        case LOCKS: {
            for (ServerSession s : database.getSessions(false)) {
                if (admin || s == session) {
                    for (Lock lock : s.getLocks()) {
                        add(rows,
                                // SESSION_ID
                                "" + s.getId(),
                                // LOCK_TYPE
                                lock.getLockType() + " "
                                        + (lock.isLockedExclusivelyBy(s) ? "WRITE" : "READ"));
                    }
                }
            }
            break;
        }
        case SESSION_STATE: {
            for (String name : session.getVariableNames()) {
                Value v = session.getVariable(name);
                add(rows,
                        // KEY
                        "@" + name,
                        // SQL
                        "SET @" + name + " " + v.getSQL());
            }
            for (Table table : session.getLocalTempTables()) {
                add(rows,
                        // KEY
                        "TABLE " + table.getName(),
                        // SQL
                        table.getCreateSQL());
            }
            String[] path = session.getSchemaSearchPath();
            if (path != null && path.length > 0) {
                StatementBuilder buff = new StatementBuilder("SET SCHEMA_SEARCH_PATH ");
                for (String p : path) {
                    buff.appendExceptFirst(", ");
                    buff.append(StringUtils.quoteIdentifier(p));
                }
                add(rows,
                        // KEY
                        "SCHEMA_SEARCH_PATH",
                        // SQL
                        buff.toString());
            }
            String schema = session.getCurrentSchemaName();
            if (schema != null) {
                add(rows,
                        // KEY
                        "SCHEMA",
                        // SQL
                        "SET SCHEMA " + StringUtils.quoteIdentifier(schema));
            }
            break;
        }
        case DATABASES: {
            List<Database> databases;
            if (LealoneDatabase.isSuperAdmin(session)) {
                databases = LealoneDatabase.getInstance().getDatabases();
            } else {
                databases = new ArrayList<>(1);
                databases.add(session.getDatabase());
            }
            for (Database database : databases) {
                add(rows,
                        // ID
                        database.getId() + "",
                        // DATABASE_NAME
                        identifier(database.getShortName()),
                        // RUN_MODE
                        database.getRunMode().toString(),
                        // NODES
                        database.getTargetNodes());
            }
            break;
        }
        case DB_OBJECTS: {
            Cursor cursor = session.getDatabase().getMetaCursor(session);
            while (cursor.next()) {
                Row row = cursor.get();
                DbObjectType type = DbObjectType.TYPES[row.getValue(1).getInt()];
                if (type == DbObjectType.USER)
                    continue;
                add(rows,
                        // ID
                        row.getValue(0).getString(),
                        // TYPE
                        type.name(),
                        // SQL
                        row.getValue(2).getString());
            }
            break;
        }
        case SERVICES: {
            for (SchemaObject obj : database.getAllSchemaObjects(DbObjectType.SERVICE)) {
                Service service = (Service) obj;
                add(rows,
                        // ID
                        "" + service.getId(), catalog, identifier(service.getSchema().getName()),
                        identifier(service.getName()),
                        // SQL
                        service.getCreateSQL());
            }
            break;
        }
        case PLUGINS: {
            List<PluginObject> pluginObjects;
            if (LealoneDatabase.isSuperAdmin(session)) {
                pluginObjects = LealoneDatabase.getInstance().getAllPluginObjects();
            } else {
                pluginObjects = new ArrayList<>(0);
            }
            for (PluginObject pluginObject : pluginObjects) {
                add(rows,
                        // ID
                        "" + pluginObject.getId(),
                        // CATALOG
                        catalog,
                        // NAME
                        identifier(pluginObject.getName()),
                        // STATE
                        pluginObject.getState(),
                        // SQL
                        pluginObject.getCreateSQL());
            }
            break;
        }
        default:
            DbException.throwInternalError("type=" + type);
        }
        return rows;
    }

    private static int getRefAction(int action) {
        switch (action) {
        case ConstraintReferential.CASCADE:
            return DatabaseMetaData.importedKeyCascade;
        case ConstraintReferential.RESTRICT:
            return DatabaseMetaData.importedKeyRestrict;
        case ConstraintReferential.SET_DEFAULT:
            return DatabaseMetaData.importedKeySetDefault;
        case ConstraintReferential.SET_NULL:
            return DatabaseMetaData.importedKeySetNull;
        default:
            throw DbException.getInternalError("action=" + action);
        }
    }

    private void addPrivileges(ArrayList<Row> rows, DbObject grantee, String catalog, Table table,
            String column, int rightMask) {
        if ((rightMask & Right.SELECT) != 0) {
            addPrivilege(rows, grantee, catalog, table, column, "SELECT");
        }
        if ((rightMask & Right.INSERT) != 0) {
            addPrivilege(rows, grantee, catalog, table, column, "INSERT");
        }
        if ((rightMask & Right.UPDATE) != 0) {
            addPrivilege(rows, grantee, catalog, table, column, "UPDATE");
        }
        if ((rightMask & Right.DELETE) != 0) {
            addPrivilege(rows, grantee, catalog, table, column, "DELETE");
        }
    }

    private void addPrivilege(ArrayList<Row> rows, DbObject grantee, String catalog, Table table,
            String column, String right) {
        String isGrantable = "NO";
        if (grantee.getType() == DbObjectType.USER) {
            User user = (User) grantee;
            if (user.isAdmin()) {
                // the right is grantable if the grantee is an admin
                isGrantable = "YES";
            }
        }
        if (column == null) {
            add(rows,
                    // GRANTOR
                    null,
                    // GRANTEE
                    identifier(grantee.getName()),
                    // TABLE_CATALOG
                    catalog,
                    // TABLE_SCHEMA
                    identifier(table.getSchema().getName()),
                    // TABLE_NAME
                    identifier(table.getName()),
                    // PRIVILEGE_TYPE
                    right,
                    // IS_GRANTABLE
                    isGrantable);
        } else {
            add(rows,
                    // GRANTOR
                    null,
                    // GRANTEE
                    identifier(grantee.getName()),
                    // TABLE_CATALOG
                    catalog,
                    // TABLE_SCHEMA
                    identifier(table.getSchema().getName()),
                    // TABLE_NAME
                    identifier(table.getName()),
                    // COLUMN_NAME
                    identifier(column),
                    // PRIVILEGE_TYPE
                    right,
                    // IS_GRANTABLE
                    isGrantable);
        }
    }

    @Override
    public long getMaxDataModificationId() {
        switch (type) {
        case SETTINGS:
        case SESSIONS:
        case LOCKS:
        case SESSION_STATE:
            return Long.MAX_VALUE;
        }
        return database.getModificationDataId();
    }

    private void addPlugin(Class<? extends Plugin> clz, ArrayList<Row> rows) {
        HashSet<String> names = new HashSet<>();
        for (Plugin p : PluginManager.getPlugins(clz)) {
            if (names.add(p.getName()) && p.getConfig() != null) {
                for (Entry<String, String> e : p.getConfig().entrySet()) {
                    add(rows, e.getKey(), clz.getSimpleName() + "(" + p.getName() + ")", e.getValue());
                }
            }
        }
    }
}
