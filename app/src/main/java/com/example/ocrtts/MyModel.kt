// set variables, run ocr

package com.example.ocrtts

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.googlecode.tesseract.android.TessBaseAPI

class MyModel internal constructor() {
    //const
    val PICTURE_REQUEST_CODE = 100
    val CREATE_REQUEST_CODE = 101
    val EDIT_REQUEST_CODE = 102
    val VIEW_RESULT_SET = 0
    val VIEW_READING_STATE = 1
    val VIEW_READ_HIGHLIGHT = 2
    val VIEW_RESET = 3
    val VIEW_BUTTON_IMG = 4
    val VIEW_PROGRESS_ING = 5
    val VIEW_TRANS_DONE = 6
    val PREFS_NAME = "MyPrefs"
    val SAVE_GUIDE_AGAIN = "save_guide_again"
    val TAG = "TextToSpeech"
    val MIME_TEXT = "text/plain"
    val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
    val mediaFolder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        MediaStore.Images.ImageColumns.RELATIVE_PATH
    else MediaStore.MediaColumns.BUCKET_DISPLAY_NAME

    //OCR
    var sTess: TessBaseAPI? = null
    var dataPath = "" //can
    var lang = "kor"//can
    var ocrIndex = -1 //OCR 진행 중인 이미지 번호
    var threadIndex = 0 //thread 시행횟수
    var folderTotalPage = 0 //전체 페이지 수

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
    val folderMetaList = arrayListOf<FolderMeta>()

    //Service
    var mIsBound = false

    private fun setFolderMeta(folder: FolderMeta, main: MainActivity): Int {
        folder.page = main.myDBHelper!!.getContinuePage(folder.title)
        Log.i("runOCR", "선택한 폴더(책 제목) : " + folder.title)
        folder.pickedNumber = folder.uriList.size
        folder.folderTotalPages += folder.pickedNumber - folder.page
        folderTotalPage += folder.folderTotalPages
        if (main.myDBHelper!!.isNewTitle(folder.title)) {
            folder.isPageUpdated = false
            Toast.makeText(main, "변환을 시작합니다.", Toast.LENGTH_LONG).show()
        } else if (folder.page < folder.pickedNumber) {
            folder.isPageUpdated = false
            Toast.makeText(main, "이전 변환에 이어서 변환합니다.", Toast.LENGTH_LONG).show()
        } else Toast.makeText(main, "완료한 변환입니다.\n다시 변환을 원할 시 변환 기록을 지워주세요", Toast.LENGTH_LONG).show()
        return if (folder.page < folder.pickedNumber) 1 else 0
    }

    fun runOCR(main: MainActivity) {
        // OCR translate
        val intent: Intent
        var validCnt = 0

        ocrIndex = 0
        // image meta data parsing
        for (f in folderMetaList) validCnt += setFolderMeta(f, main)
        if (0 < validCnt) {
            intent = Intent(main, TransService::class.java).putExtra("pageNum", folderTotalPage)
            mIsBound = main.bindService(intent, main.mConnection, AppCompatActivity.BIND_AUTO_CREATE)
        }
    }
}