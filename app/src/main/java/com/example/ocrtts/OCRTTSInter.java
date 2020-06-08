package com.example.ocrtts;

public interface OCRTTSInter {
    //Request Code
    int PICTURE_REQUEST_CODE = 100;
    int CREATE_REQUEST_CODE = 101;
    int EDIT_REQUEST_CODE = 102;
    int VIEW_RESULT_SET = 0;
    int VIEW_READING_STATE = 1;
    int VIEW_READ_HIGHLIGHT = 2;
    int VIEW_RESET = 3;
    int VIEW_MAIN_PROGRESS = 4;
    int VIEW_TRANS_DONE = 5;
    int VIEW_BUTTON_IMG = 6;

    String TAG = "TextToSpeech";
}
