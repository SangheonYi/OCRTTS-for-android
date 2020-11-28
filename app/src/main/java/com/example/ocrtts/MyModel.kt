package com.example.ocrtts

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI

class MyModel internal constructor() {
    //const
    val PICTURE_REQUEST_CODE = 100
    val CREATE_REQUEST_CODE = 101
    val EDIT_REQUEST_CODE = 102
    val FOLDER_REQUEST_CODE = 103
    val VIEW_RESULT_SET = 0
    val VIEW_READING_STATE = 1
    val VIEW_READ_HIGHLIGHT = 2
    val VIEW_RESET = 3
    val VIEW_PROGRESS_ING = 4
    val VIEW_TRANS_DONE = 5
    val VIEW_BUTTON_IMG = 6
    val TAG = "TextToSpeech"
    val MIME_TEXT = "text/plain"

    //OCR
    var sTess: TessBaseAPI? = null
    var dataPath = "" //can
    var lang = "kor"//can
    var ocrIndex = -1 //OCR 진행 중인 이미지 번호
    var threadIndex = 0 //thread 시행횟수
    var totalPageNum = 0
//    var clipData: ClipData? = null
    var uriList = ArrayList<Uri>()

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
    var pickedNumber = 0
    var isPageUpdated = false
    val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"

    //Service
    var mIsBound = false

    fun allocClipData(requestCode: Int, data: Intent?, main: MainActivity) {
        val curs: Cursor?

        when(requestCode) {
            PICTURE_REQUEST_CODE -> {
                if (data != null)
                if (data.data != null) {
                    // 이미지 한 장만 선택했을 때
                    uriList.add(data.data!!)
                    Log.i("DB", "clipData : " + uriList)
                } else if (data.clipData != null)
                    for (i in 0 until data.clipData!!.itemCount)
                        uriList.add(data.clipData!!.getItemAt(i).uri)
            }
            FOLDER_REQUEST_CODE -> {
                Log.i("DB", "FOLDER_REQUEST_CODE")
            }
        }
        if (uriList.isNotEmpty()) {
            curs = main.contentResolver.query(uriList[0],
                    arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
                    null, null, null)
            if (curs!!.moveToNext()) {
                Log.i("title: ", curs.getString(0))
                title = curs.getString(0)
            }
            curs.close()
        }
    }

    fun imageMetaCheck() {

    }
}