package com.github.davols.dasftp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Created by davols on 17.08.14.
 */
public class daSftpProvider extends ContentProvider{

    public static final String TABLE_NAME_UPLOADS = "myuploads";
    public static final String KEY_ID = "_id";
    public static final String KEY_URL = "murl";
    // private static final int TODO_ID = 20;
    public static final String KEY_NAME = "mname";
    public static final String KEY_UPL_NAME = "uplname";
    public static final String KEY_DATE_UPLOADED = "uploaded";
    public static final String KEY_FILEPATH = "filepath";
    public static final String TABLE_NAME_SITES = "mysites";

    // private static final int TODO_ID = 20;
    public static final String KEY_HOST = "mhostning";
    public static final String KEY_USERNAME = "musername";
    public static final String KEY_PASSWORD = "mpassword";
    public static final String KEY_PATH = "mpath";
    public static final String KEY_PORT = "mport";
    public static final String KEY_SIGNED = "msigned";

    // For imgur or sftp.
    public static final String KEY_SITE_TYPE = "sitetype";

    // Used for debugging and logging
    private static final String TAG = "dasftpProvider";
    /**
     * The database that the provider uses as its underlying data store
     */
    private static final String DATABASE_NAME = "dasftp5.db";
    /**
     * The database version
     */
    private static final int DATABASE_VERSION = 18;
    private static final int MYUPLOADS = 10;
    private static final int MYSITES = 20;
    private static final String AUTHORITY = "com.github.davols.dasftp";
    private static final String BASE_PATH = "myuploads";
    public static final Uri CONTENT_URI_UPLOADS = Uri.parse("content://" + AUTHORITY
            + "/" + BASE_PATH);
    private static final String BASE_PATH_SITES = "mysites";
    public static final Uri CONTENT_URI_SITES = Uri.parse("content://" + AUTHORITY
            + "/" + BASE_PATH_SITES);
    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sURIMatcher.addURI(AUTHORITY, BASE_PATH, MYUPLOADS);
        sURIMatcher.addURI(AUTHORITY, BASE_PATH_SITES, MYSITES);

    }

    /*
     * Constants used by the Uri matcher to choose an action based on the pattern
     * of the incoming URI
     */
    // The incoming URI matches the Notes URI pattern
    private static final int NOTES = 1;
    // Handle to a new DatabaseHelper.
    private DatabaseHelper mOpenHelper;

    /**
     * Initializes the provider by creating a new DatabaseHelper. onCreate() is called
     * automatically when Android creates the provider in response to a resolver request from a
     * client.
     */
    @Override
    public boolean onCreate() {

        // Creates a new helper object. Note that the database itself isn't opened until
        // something tries to access it, and it's only created if it doesn't already exist.
        mOpenHelper = new DatabaseHelper(getContext());

        // Assumes that any failures will be reported by a thrown exception.
        return true;
    }

    /**
     * This method is called when a client calls
     * {@link android.content.ContentResolver#query(android.net.Uri, String[], String, String[], String)}.
     * Queries the database and returns a cursor containing the results.
     *
     * @return A cursor containing the results of the query. The cursor exists but is empty if
     * the query returns no results or an exception occurs.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();

        // Uisng SQLiteQueryBuilder instead of query() method
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();

        // check if the caller has requested a column which does not exists
        checkColumns(projection);

        // Set the table


        int uriType = sURIMatcher.match(uri);
        switch (uriType) {
            case MYUPLOADS:
                queryBuilder.setTables(TABLE_NAME_UPLOADS);
                break;
            case MYSITES:
                queryBuilder.setTables(TABLE_NAME_SITES);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }


        Cursor cursor = queryBuilder.query(db, projection, selection,
                selectionArgs, null, null, sortOrder);
        // make sure that potential listeners are getting notified
        cursor.setNotificationUri(getContext().getContentResolver(), uri);

        return cursor;
    }

    private void checkColumns(String[] projection) {
        String[] available = {KEY_ID,
                KEY_DATE_UPLOADED, KEY_NAME, KEY_SITE_TYPE,
                KEY_UPL_NAME, KEY_URL, KEY_FILEPATH, KEY_URL, KEY_HOST, KEY_USERNAME, KEY_PASSWORD,
                KEY_PATH,KEY_PORT,KEY_SIGNED};
        if (projection != null) {
            HashSet<String> requestedColumns = new HashSet<String>(Arrays.asList(projection));
            HashSet<String> availableColumns = new HashSet<String>(Arrays.asList(available));
            // check if all columns which are requested are available
            if (!availableColumns.containsAll(requestedColumns)) {
                throw new IllegalArgumentException("Unknown columns in projection");
            }
        }
    }

    /**
     * This is called when a client calls {@link android.content.ContentResolver#getType(android.net.Uri)}.
     * Returns the MIME data type of the URI given as a parameter.
     *
     * @param uri The URI whose MIME type is desired.
     * @return The MIME type of the URI.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public String getType(Uri uri) {

        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        int uriType = sURIMatcher.match(uri);
        SQLiteDatabase sqlDB = mOpenHelper.getWritableDatabase();
        Log.d(TAG,"uriType:"+uriType);
        long id = 0;
        switch (uriType) {
            case MYUPLOADS:
                Log.d("Main", "inserted");
                id = sqlDB.insert(TABLE_NAME_UPLOADS, null, values);
                break;
            case MYSITES:
                Log.d("Main", "inserted");
                id = sqlDB.insert(TABLE_NAME_SITES, null, values);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return Uri.parse(BASE_PATH + "/" + id);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int uriType = sURIMatcher.match(uri);
        Log.d(TAG,"uriType:"+uriType);
        SQLiteDatabase sqlDB = mOpenHelper.getWritableDatabase();
        int rowsDeleted = 0;
        switch (uriType) {
            case MYUPLOADS:
                rowsDeleted = sqlDB.delete(TABLE_NAME_UPLOADS, selection,
                        selectionArgs);
                break;
            case MYSITES:
                rowsDeleted = sqlDB.delete(TABLE_NAME_SITES, selection,
                        selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return rowsDeleted;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {

        int uriType = sURIMatcher.match(uri);

        Log.d(TAG,"uriType:"+uriType);
        SQLiteDatabase sqlDB = mOpenHelper.getWritableDatabase();
        int rowsUpdated = 0;
        switch (uriType) {
            case MYUPLOADS:
                rowsUpdated = sqlDB.update(TABLE_NAME_UPLOADS,
                        values,
                        selection,
                        selectionArgs);
                break;
            case MYSITES:
                rowsUpdated = sqlDB.update(TABLE_NAME_SITES,
                        values,
                        selection,
                        selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return rowsUpdated;
    }

    /**
     * This class helps open, create, and upgrade the database file. Set to package visibility
     * for testing purposes.
     */
    static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {

            // calls the super constructor, requesting the default cursor factory.
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        /**
         * Creates the underlying database with table name and column names taken from the
         * NotePad class.
         */
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_NAME_UPLOADS + " ("
                    + KEY_ID + " INTEGER PRIMARY KEY,"
                    + KEY_URL + " TEXT,"
                    + KEY_NAME + " TEXT,"
                    + KEY_UPL_NAME + " TEXT,"
                    + KEY_FILEPATH + " TEXT,"
                    + KEY_SITE_TYPE + " TEXT,"
                    + KEY_DATE_UPLOADED + " INTEGER"
                    + ");");
            db.execSQL("CREATE TABLE " + TABLE_NAME_SITES + " ("
                    + KEY_ID + " INTEGER PRIMARY KEY,"
                    + KEY_URL + " TEXT,"
                    + KEY_USERNAME + " TEXT,"
                    + KEY_PASSWORD + " TEXT,"
                    + KEY_PATH + " TEXT,"
                    + KEY_HOST + " TEXT,"
                    + KEY_PORT + " INTEGER,"
                    + KEY_SITE_TYPE + " TEXT,"
                    + KEY_SIGNED + " INTEGER"
                    + ");");
        }

        /**
         * Demonstrates that the provider must consider what happens when the
         * underlying datastore is changed. In this sample, the database is upgraded the database
         * by destroying the existing data.
         * A real application should upgrade the database in place.
         */
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            // Logs that the database is being upgraded
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");

            // Kills the table and existing data
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_UPLOADS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_SITES);
            // Recreates the database with a new version
            onCreate(db);
        }
    }
}