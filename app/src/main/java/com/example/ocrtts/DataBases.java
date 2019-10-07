package com.example.ocrtts;

import android.provider.BaseColumns;

public final class DataBases {

    public  static final class CreateDB implements BaseColumns{
        public static final String TITLE = "title";
        public static final String LAST_PAGE = "last_page";
        public static final String VIEW_DATA = "title_last_page";
        public static final String CHECK_BOOL = "check_bool";
        public static final String _TABLENAME0 = "booktable";
        public static final String _CREATE0 = "create table if not exists "+_TABLENAME0+"("
                + _ID+ " integer primary key autoincrement, "
                + TITLE+ " text not null , "
                + LAST_PAGE + " int not null , "
                + VIEW_DATA + " text not null , "
                + CHECK_BOOL + " int not null );";
    }
}
