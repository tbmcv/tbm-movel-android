package net.tbmcv.tbmmovel;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.test.AndroidTestCase;

public class TestingContentProviderTest extends AndroidTestCase {
    private static final Uri baseUri = Uri.parse("content://theboss/tbl");

    public void testQueryByValue() {
        TestingContentProvider provider = new TestingContentProvider(baseUri.getAuthority())
                .addTable("tbl", "id", "x", "y");
        provider.onCreate();
        ContentValues values = new ContentValues();
        long id = 123;
        String x = "hello";
        int y = -3;
        values.put("id", id);
        values.put("x", x);
        values.put("y", y);
        provider.getDatabase().insertOrThrow("tbl", null, values);
        provider.getDatabase().insertOrThrow("tbl", "id", null);
        Cursor cursor = provider.query(baseUri, new String[] { "y", "id" },
                "x = ?", new String[] { x }, null);
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals(2, cursor.getColumnCount());
            assertEquals(y, cursor.getInt(0));
            assertEquals(id, cursor.getLong(1));
            assertFalse(cursor.moveToNext());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void testQuery() {
        TestingContentProvider provider = new TestingContentProvider(baseUri.getAuthority())
                .addTable("tbl", "id", "x", "y");
        provider.onCreate();
        ContentValues values = new ContentValues();
        long id = 123;
        String x = "hello";
        float y = 17.25f;
        values.put("id", id);
        values.put("x", x);
        values.put("y", y);
        provider.getDatabase().insertOrThrow("tbl", null, values);
        provider.getDatabase().insertOrThrow("tbl", "id", null);
        Cursor cursor = provider.query(
                ContentUris.withAppendedId(baseUri, id), new String[] { "x", "y" },
                null, null, null);
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals(2, cursor.getColumnCount());
            assertEquals(x, cursor.getString(0));
            assertEquals(y, cursor.getFloat(1));
            assertFalse(cursor.moveToNext());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void testInsert() {
        TestingContentProvider provider = new TestingContentProvider(baseUri.getAuthority())
                .addTable("tbl", "id", "x", "y");
        provider.onCreate();
        ContentValues values = new ContentValues();
        double x = 1.5;
        String y = "...";
        values.put("x", x);
        values.put("y", y);
        Uri uri = provider.insert(baseUri, values);
        long id = ContentUris.parseId(uri);
        Cursor cursor = provider.getDatabase().query("tbl", new String[] { "y", "x" },
                "id = ?", new String[]{ Long.toString(id) }, null, null, null);
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals(2, cursor.getColumnCount());
            assertEquals(y, cursor.getString(0));
            assertEquals(x, cursor.getDouble(1));
            assertFalse(cursor.moveToNext());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void testDelete() {
        TestingContentProvider provider = new TestingContentProvider(baseUri.getAuthority())
                .addTable("tbl", "id", "x", "y");
        provider.onCreate();
        long id = provider.getDatabase().insertOrThrow("tbl", "id", null);
        provider.getDatabase().insertOrThrow("tbl", "id", null);
        int nRows = provider.delete(ContentUris.withAppendedId(baseUri, id), null, null);
        assertEquals(1, nRows);
    }

    public void testDeleteByValue() {
        TestingContentProvider provider = new TestingContentProvider(baseUri.getAuthority())
                .addTable("tbl", "id", "x", "y");
        provider.onCreate();
        ContentValues values = new ContentValues();
        long id = 5;
        int x = 10;
        String y = "la";
        values.put("id", id);
        values.put("x", x);
        values.put("y", y);
        provider.getDatabase().insertOrThrow("tbl", null, values);
        provider.getDatabase().insertOrThrow("tbl", "id", null);
        int nRows = provider.delete(baseUri, "y = ?", new String[] { y });
        assertEquals(1, nRows);
    }
}
