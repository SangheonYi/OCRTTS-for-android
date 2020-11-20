package com.example.ocrtts

import android.content.ClipData
import android.net.Uri

class MyModel internal constructor() {
    //const
    val PICTURE_REQUEST_CODE = 100
    val CREATE_REQUEST_CODE = 101
    val EDIT_REQUEST_CODE = 102
    val VIEW_RESULT_SET = 0
    val VIEW_READING_STATE = 1
    val VIEW_READ_HIGHLIGHT = 2
    val VIEW_RESET = 3
    val VIEW_MAIN_PROGRESS = 4
    val VIEW_TRANS_DONE = 5
    val VIEW_BUTTON_IMG = 6
    val TAG = "TextToSpeech"
    val MIME_TEXT = "text/plain"

    //OCR
    var dataPath = "" //can
    var lang = "kor"//can
    var ocrIndex = -1 //OCR 진행 중인 이미지 번호
    var threadIndex = 0 //thread 시행횟수
    var totalPageNum = 0
    var clipData: ClipData? = null

    //Text, TTS
    var ocrResult = " " //OCR 결과값 받음
    var bigText = BigText()//Sentence를 가지는 class
    var readIndex = 0 //읽고있는 문장 넘버
    var state = "stop"//playing, stop
    var charSum = 0 //읽고 있는 글자의 view에서의 위치
    var readSpeed = 3.0 //tts 기본속도 설정
    var readState = "현재 문장 " + bigText.size + " 중 " + readIndex + "번 문장"

    //Data
    var frw = SAFRW()
    var safUri: Uri? = null

    var title = "no title"
    var page = 0
    var titleLastPage = "$title\nPage: $page"
    var isPageUpdated = false

    //Service
    var mIsBound = false
}