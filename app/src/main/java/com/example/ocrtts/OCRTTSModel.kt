package com.example.ocrtts

import android.content.ClipData
import android.net.Uri
import java.io.Serializable

class OCRTTSModel internal constructor() : Serializable {
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
    var MIME_TEXT = "text/plain"
    var title = "no title"
    var page = 0
    var titleLastPage = "$title\nPage: $page"
    var isPageUpdated = false

    //Service
    var mIsBound = false
}