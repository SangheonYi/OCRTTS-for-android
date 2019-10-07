package com.example.ocrtts;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class MyDatabaseOpenHelper {

    private static final String DATABASE_NAME = "InnerDatabase(SQLite).db";
    private static final int DATABASE_VERSION = 1;
    public static SQLiteDatabase mDB;
    private DatabaseHelper mDBHelper;
    private Context mCtx;

    private class DatabaseHelper extends SQLiteOpenHelper{

        public DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
            super(context, name, factory, version);
        }

        @Override
        public void onCreate(SQLiteDatabase db){
            db.execSQL(DataBases.CreateDB._CREATE0);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS "+DataBases.CreateDB._TABLENAME0);
            onCreate(db);
        }
    }

    public MyDatabaseOpenHelper(Context context){
        this.mCtx = context;
    }

    public MyDatabaseOpenHelper open() throws SQLException{
        mDBHelper = new DatabaseHelper(mCtx, DATABASE_NAME, null, DATABASE_VERSION);
        mDB = mDBHelper.getWritableDatabase();
        return this;
    }

    public void create(){
        mDBHelper.onCreate(mDB);
    }

    public void close(){
        mDB.close();
    }

    // Insert DB
    public long insertColumn(String title, long last_page, String title_last_page, long check_bool){
        ContentValues values = new ContentValues();
        values.put(DataBases.CreateDB.TITLE, title);
        values.put(DataBases.CreateDB.LAST_PAGE, last_page);
        values.put(DataBases.CreateDB.VIEW_DATA, title_last_page);
        values.put(DataBases.CreateDB.CHECK_BOOL, check_bool);
        return mDB.insert(DataBases.CreateDB._TABLENAME0, null, values);
    }

    // Update DB
    public boolean updateColumn(long id, String title, long last_page, String title_last_page, long check_bool){
        ContentValues values = new ContentValues();
        values.put(DataBases.CreateDB.TITLE, title);
        values.put(DataBases.CreateDB.LAST_PAGE, last_page);
        values.put(DataBases.CreateDB.VIEW_DATA, title_last_page);
        values.put(DataBases.CreateDB.CHECK_BOOL, check_bool);
        Log.i("DB", " mDB.update(DataBases.CreateDB._TABLENAME0, values, \"_id=\" + id, null) :  " + mDB.update(DataBases.CreateDB._TABLENAME0, values, "_id=" + id, null));
        Log.i("DB", " mDB.update(DataBases.CreateDB._TABLENAME0, values, \"_id=\" + id, null) :  id:: " + id + " " + " title:: " + title + " " + " last_page:: " + last_page + " " + " title_last_page:: " + title_last_page + " " + " check_bool:: " + check_bool);
        return mDB.update(DataBases.CreateDB._TABLENAME0, values, "_id=" + id, null) > 0;
    }

    // Delete All
    public void deleteAllColumns(int tableIndex) {
        mDB.delete(DataBases.CreateDB._TABLENAME0, null, null);
    }

    // Delete DB
    public boolean deleteTuple(long id, int tableIndex){
        return mDB.delete(DataBases.CreateDB._TABLENAME0, "_id="+id, null) > 0;
    }
    // Select DB
    public Cursor selectColumns(int tableIndex){
        return mDB.query(DataBases.CreateDB._TABLENAME0, null, null, null, null, null, null);
    }

    // sort by column
    public Cursor sortColumn(String sort){
        Cursor c = mDB.rawQuery( "SELECT * FROM booktable ORDER BY " + sort + ";", null);
        return c;
    }

    public boolean isNewTitle(String title){
        String dbTitle = "";
        Cursor  cursor = getCursorByTitle(title);

        while(cursor.moveToNext()){
            dbTitle = cursor.getString(1);
            Log.i("DB", "DB에서 cursor.getString(1) : " + dbTitle + " cursor.getString(1) : " + cursor.getString(2) + " pathArray[pathArray.length-2] : " + title);
        }

        if (dbTitle.equals(title)){
            Log.i("DB", "isNew에서 false ");
            return false;
        }
        Log.i("DB", "isNew에서 True ");
        return true;
    }

    public int getContinuePage(String title){
        int pageIndex = 0;
        Cursor  cursor = getCursorByTitle(title);
        while(cursor.moveToNext()){
            if (cursor.getInt(2)>=0)
                pageIndex = cursor.getInt(2);
            Log.i("DB", "DB에서 뽑은 값 " + pageIndex);
        }

        Log.i("DB", "getContinuePage에서 return " + pageIndex);
        return pageIndex;
    }

    public long getIdByTitle(String title){
        long id = -1;
        Cursor  cursor = getCursorByTitle(title);
        while(cursor.moveToNext()){
            if (cursor.getInt(2)>=0)
                id = cursor.getInt(0);
            Log.i("DB", "getIdByTitle에서 뽑은 값 " + id);
        }

        return id;
    }

    public Cursor getCursorByTitle(String title){
        String query = "SELECT * FROM booktable WHERE title='" + title + "'";
        Cursor  cursor = mDB.rawQuery(query,null);
        return cursor;
    }
}
