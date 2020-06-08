package com.example.ocrtts;

import android.content.ClipData;
import android.net.Uri;

import java.io.Serializable;

class OCRTTSModel implements Serializable {
    //OCR
    String datapath;//can
    String lang;//can
    int OCRIndex;//OCR 진행 중인 이미지 번호
    int threadIndex;//thread 시행횟수
    int totalPageNum;
    ClipData clipData;

    //Text, TTS
    String OCRresult;//OCR 결과값 받음
    BigText bigText;//Sentence를 가지는 class
    int ReadIndex;//읽고있는 문장 넘버
    String State;//playing, stop
    int CharSum;//읽고 있는 글자의 view에서의 위치
    String Reading_State;
    double readSpeed; //tts 기본속도 설정

    //Data
    SAFRW frw;
    Uri SAFUri;
    String MIME_TEXT;
    String Title;
    int Page;
    String title_last_page ;
    boolean isPageUpdated;

    //Service
    boolean mIsBound;

    OCRTTSModel(){
        //OCR
        datapath = "";
        lang = "kor";
        OCRIndex = -1;//OCR 진행 중인 이미지 번호
        threadIndex = 0;//thread 시행횟수
        totalPageNum = 0;

        //Text, TTS
        OCRresult = " ";//OCR 결과값 받음
        bigText = new BigText();//Sentence를 가지는 class
        ReadIndex = 0;//읽고있는 문장 넘버
        State = "stop";//playing, stop
        CharSum = 0;//읽고 있는 글자의 view에서의 위치
        Reading_State = "현재 문장 " + bigText.getSize() + " 중 " + ReadIndex + "번 문장";
        readSpeed = 3.0; //tts 기본속도 설정

        //Data
        frw = new SAFRW();
        MIME_TEXT = "text/plain";
        Title = "no title";
        Page = 0;
        title_last_page = Title + "\nPage: " + Page;
        isPageUpdated = false;
    }
}
