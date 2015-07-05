package net.tbmcv.tbmmovel;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestingContentProvider extends ContentProvider {
    private static final String LOG_TAG = "TestingContentProvider";

    private final List<TableDefinition> tables = new ArrayList<>();
    private final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    private final String authority;
    private SQLiteDatabase database;

    public TestingContentProvider(String authority) {
        this.authority = authority;
    }

    public static class TableDefinition {
        public final String name;
        public final String idColumn;
        public final String[] otherColumns;

        public TableDefinition(String name, String idColumn, String... otherColumns) {
            this.name = name;
            this.idColumn = idColumn;
            this.otherColumns = otherColumns;
        }
    }

    public SQLiteDatabase getDatabase() {
        return database;
    }

    public TestingContentProvider addTable(TableDefinition table) {
        int tableIndex = tables.size();
        tables.add(table);
        uriMatcher.addURI(authority, table.name, tableIndex << 1);
        uriMatcher.addURI(authority, table.name + "/#", (tableIndex << 1) + 1);
        return this;
    }

    public TestingContentProvider addTable(String name, String idColumn, String... otherColumns) {
        return addTable(new TableDefinition(name, idColumn, otherColumns));
    }

    private static boolean hasId(int tableCode) {
        return (tableCode & 1) != 0;
    }

    private static int getTableIndex(int tableCode) {
        return tableCode >> 1;
    }

    @Override
    public boolean onCreate() {
        database = SQLiteDatabase.create(null);
        database.beginTransaction();
        try {
            initSchema(database);
            database.setTransactionSuccessful();
            return true;
        } catch (SQLException e) {
            Log.e(LOG_TAG, "Error initializing schema", e);
            return false;
        } finally {
            database.endTransaction();
        }
    }

    protected void initSchema(SQLiteDatabase database) throws SQLException {
        for (TableDefinition table : tables) {
            StringBuilder sqlBuilder = new StringBuilder("CREATE TABLE ")
                    .append(table.name)
                    .append('(')
                    .append(table.idColumn)
                    .append(" integer primary key");
            for (String column : table.otherColumns) {
                sqlBuilder.append(',');
                sqlBuilder.append(column);
            }
            database.execSQL(sqlBuilder.append(");").toString());
        }
    }

    private static class SelectionParams {
        final TableDefinition table;
        final String selection;
        final String[] selectionArgs;

        SelectionParams(TableDefinition table, String selection, String[] selectionArgs) {
            this.table = table;
            this.selection = selection;
            this.selectionArgs = selectionArgs;
        }

        @Override
        public String toString() {
            return "SelectionParams[" + table.name + " where " + selection
                    + " " + Arrays.toString(selectionArgs) + "]";
        }
    }

    private SelectionParams getSelectionParams(Uri uri, String selection, String[] selectionArgs) {
        int tableCode = uriMatcher.match(uri);
        if (tableCode == UriMatcher.NO_MATCH) {
            Log.e(LOG_TAG, "Invalid URI: " + uri);
            return null;
        }
        TableDefinition table = tables.get(getTableIndex(tableCode));
        if (hasId(tableCode)) {
            if (selection != null) {
                selection = "(" + selection + ") and " + table.idColumn + " = ?";
            } else {
                selection = table.idColumn + " = ?";
            }
            if (selectionArgs != null) {
                String[] newSelectionArgs = new String[selectionArgs.length + 1];
                System.arraycopy(selectionArgs, 0, newSelectionArgs, 0, selectionArgs.length);
                selectionArgs = newSelectionArgs;
            } else {
                selectionArgs = new String[1];
            }
            selectionArgs[selectionArgs.length - 1] = String.valueOf(ContentUris.parseId(uri));
        }
        return new SelectionParams(table, selection, selectionArgs);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        SelectionParams params = getSelectionParams(uri, selection, selectionArgs);
        if (params == null) {
            return null;
        }
        Log.d(LOG_TAG, "select: " + Arrays.asList(projection)
                + " from " + params + " order by " + sortOrder);
        return database.query(params.table.name, projection,
                params.selection, params.selectionArgs, null, null, sortOrder);
    }

    @Override
    public String getType(Uri uri) {
        int tableCode = uriMatcher.match(uri);
        if (tableCode == UriMatcher.NO_MATCH) {
            Log.e(LOG_TAG, "Invalid URI: " + uri);
            return null;
        }
        TableDefinition table = tables.get(getTableIndex(tableCode));
        return "vnd.android.cursor." + (hasId(tableCode) ? "item/" : "dir/") + table.name;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        int tableCode = uriMatcher.match(uri);
        if (tableCode == UriMatcher.NO_MATCH) {
            Log.e(LOG_TAG, "Invalid URI: " + uri);
            return null;
        }
        TableDefinition table = tables.get(getTableIndex(tableCode));
        if (hasId(tableCode)) {
            values = new ContentValues(values);
            values.put(table.idColumn, ContentUris.parseId(uri));
        }
        long id = database.insert(table.name, table.idColumn, values);
        if (id == -1) {
            return null;
        } else if (hasId(tableCode)) {
            return uri;
        } else {
            return ContentUris.withAppendedId(uri, id);
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SelectionParams params = getSelectionParams(uri, selection, selectionArgs);
        if (params == null) {
            return 0;
        }
        Log.d(LOG_TAG, "delete: " + params);
        return database.delete(params.table.name, params.selection, params.selectionArgs);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SelectionParams params = getSelectionParams(uri, selection, selectionArgs);
        if (params == null) {
            return 0;
        }
        Log.d(LOG_TAG, "update: " + params + " with " + values);
        return database.update(params.table.name, values, params.selection, params.selectionArgs);
    }
}
