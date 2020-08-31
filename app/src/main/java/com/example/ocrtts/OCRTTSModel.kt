package com.example.ocrtts

import android.content.ClipData
import android.net.Uri
import java.io.Serializable

class OCRTTSModel internal constructor() : Serializable {
    //OCR
    var datapath = "" //can
    var lang = "kor"//can
    var OCRIndex = -1 //OCR 진행 중인 이미지 번호
    var threadIndex = 0 //thread 시행횟수
    var totalPageNum = 0
    var clipData: ClipData? = null

    //Text, TTS
    var OCRresult = " " //OCR 결과값 받음
    var bigText = BigText()//Sentence를 가지는 class
    var ReadIndex = 0 //읽고있는 문장 넘버
    var State = "stop"//playing, stop
    var CharSum = 0 //읽고 있는 글자의 view에서의 위치
    var readSpeed = 3.0 //tts 기본속도 설정
    var Reading_State = "현재 문장 " + bigText.size + " 중 " + ReadIndex + "번 문장"

    //Data
    var frw = SAFRW()
    var SAFUri: Uri? = null
    var MIME_TEXT = "text/plain"
    var Title = "no title"
    var Page = 0
    var title_last_page = "$Title\nPage: $Page"
    var isPageUpdated = false

    //Service
    var mIsBound = false
}