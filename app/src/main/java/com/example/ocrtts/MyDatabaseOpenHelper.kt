package com.example.ocrtts

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CursorFactory
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class MyDatabaseOpenHelper(private val mCtx: Context) {
    private val DATABASE_NAME = "InnerDatabase(SQLite).db"
    private val DATABASE_VERSION = 1
    private var mDBHelper: DatabaseHelper? = null
    var mDB: SQLiteDatabase? = null

    private inner class DatabaseHelper(context: Context?, name: String?, factory: CursorFactory?, version: Int) : SQLiteOpenHelper(context, name, factory, version) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(DataBases.CreateDB._CREATE0)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS " + DataBases.CreateDB._TABLENAME0)
            onCreate(db)
        }
    }

    @Throws(SQLException::class)
    fun open(): MyDatabaseOpenHelper {
        mDBHelper = DatabaseHelper(mCtx, DATABASE_NAME, null, DATABASE_VERSION)
        mDB = mDBHelper!!.writableDatabase
        return this
    }

    fun create() {
        mDBHelper!!.onCreate(mDB!!)
    }

    fun close() {
        mDB!!.close()
    }

    // Insert DB
    fun insertColumn(title: String?, last_page: Long, title_last_page: String?, check_bool: Long): Long {
        val values = ContentValues()
        values.put(DataBases.CreateDB.TITLE, title)
        values.put(DataBases.CreateDB.LAST_PAGE, last_page)
        values.put(DataBases.CreateDB.VIEW_DATA, title_last_page)
        values.put(DataBases.CreateDB.CHECK_BOOL, check_bool)
        return mDB!!.insert(DataBases.CreateDB._TABLENAME0, null, values)
    }

    // Update DB
    fun updateColumn(id: Long, title: String, last_page: Long, title_last_page: String, check_bool: Long): Boolean {
        val values = ContentValues()
        values.put(DataBases.CreateDB.TITLE, title)
        values.put(DataBases.CreateDB.LAST_PAGE, last_page)
        values.put(DataBases.CreateDB.VIEW_DATA, title_last_page)
        values.put(DataBases.CreateDB.CHECK_BOOL, check_bool)
        Log.i("DB", " mDB.update(DataBases.CreateDB._TABLENAME0, values, \"_id=\" + id, null) :  " + mDB!!.update(DataBases.CreateDB._TABLENAME0, values, "_id=$id", null))
        Log.i("DB", " mDB.update(DataBases.CreateDB._TABLENAME0, values, \"_id=\" + id, null) :  id:: $id  title:: $title  last_page:: $last_page  title_last_page:: $title_last_page  check_bool:: $check_bool")
        return mDB!!.update(DataBases.CreateDB._TABLENAME0, values, "_id=$id", null) > 0
    }

    // Delete All
    fun deleteAllColumns(tableIndex: Int) {
        mDB!!.delete(DataBases.CreateDB._TABLENAME0, null, null)
    }

    // Delete DB
    fun deleteTuple(id: Long, tableIndex: Int): Boolean {
        return mDB!!.delete(DataBases.CreateDB._TABLENAME0, "_id=$id", null) > 0
    }

    // Select DB
    fun selectColumns(tableIndex: Int): Cursor {
        return mDB!!.query(DataBases.CreateDB._TABLENAME0, null, null, null, null, null, null)
    }

    // sort by column
    fun sortColumn(sort: String): Cursor {
        return mDB!!.rawQuery("SELECT * FROM booktable ORDER BY $sort;", null)
    }

    fun isNewTitle(title: String): Boolean {
        var dbTitle = ""
        val cursor = getCursorByTitle(title)
        while (cursor.moveToNext()) {
            dbTitle = cursor.getString(1)
            Log.i("DB", "DB에서 cursor.getString(1) : " + dbTitle + " cursor.getString(1) : " + cursor.getString(2) + " pathArray[pathArray.length-2] : " + title)
        }
        if (dbTitle == title) {
            Log.i("DB", "isNew에서 false ")
            return false
        }
        Log.i("DB", "isNew에서 True ")
        return true
    }

    fun getContinuePage(title: String): Int {
        var pageIndex = 0
        val cursor = getCursorByTitle(title)
        while (cursor.moveToNext()) {
            if (cursor.getInt(2) >= 0) pageIndex = cursor.getInt(2)
            Log.i("DB", "DB에서 뽑은 값 $pageIndex")
        }
        Log.i("DB", "getContinuePage에서 return $pageIndex")
        return pageIndex
    }

    fun getIdByTitle(title: String): Long {
        var id: Long = -1
        val cursor = getCursorByTitle(title)
        while (cursor.moveToNext()) {
            if (cursor.getInt(2) >= 0) id = cursor.getInt(0).toLong()
            Log.i("DB", "getIdByTitle에서 뽑은 값 $id")
        }
        return id
    }

    fun getCursorByTitle(title: String): Cursor {
        val query = "SELECT * FROM booktable WHERE title='$title'"
        return mDB!!.rawQuery(query, null)
    }
}