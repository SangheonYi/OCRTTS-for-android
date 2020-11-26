package com.example.ocrtts

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException

class OCR(inModel: MyModel, inMain: MainActivity)  // 초기화 작업
    : Thread() {
    private var model: MyModel = inModel
    private var main: MainActivity = inMain
    private val strBuilder = StringBuilder()
    private var transResult: String? = null
    private var urione: Uri? = null
    private lateinit var intent: Intent
    private lateinit var vibrator: Vibrator
    var image: Bitmap? = null //갤러리에서 이미지 받아와

    @Synchronized
    override fun run() {
        strBuilder.append(model.ocrResult)
        if (model.page < model.uriList.size) {
            model.ocrIndex = 0
            intent = Intent(main, TransService::class.java)
            intent.putExtra("pageNum", model.totalPageNum)
            model.mIsBound = main.bindService(intent, main.mConnection, AppCompatActivity.BIND_AUTO_CREATE)
        }
        Log.i("OCR", model.threadIndex.toString() + "번째 스레드의 run")
        while (model.page < model.uriList.size) {
            try {
                urione = model.uriList[model.page]
                image = MediaStore.Images.Media.getBitmap(main.contentResolver, urione)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            model.sTess!!.setImage(image)
            model.ocrIndex++
            model.page++
            Log.i("OCR", "getUTF8Text가 OCR변환 끝나고 값 받을 때 까지 기다림. 그냥 변환 중이란 얘기")
            transResult = model.sTess!!.utF8Text
            strBuilder.append(transResult)
            model.bigText.addSentence(transResult)
            if (model.ocrIndex < model.totalPageNum)
                main.mHandler.sendMessage(Message.obtain(main.mHandler, model.VIEW_PROGRESS_ING, 0)) //변환 과정
            model.ocrResult = strBuilder.toString()
            main.mHandler.sendMessage(Message.obtain(main.mHandler, model.VIEW_RESULT_SET, 0)) //결과 화면 set
            if (model.state == "playing") main.mHandler.sendMessage(
                    Message.obtain(main.mHandler, model.VIEW_READ_HIGHLIGHT, 0)) //읽는 중일 시 강조
        }
        main.mHandler.sendMessage(Message.obtain(main.mHandler, model.VIEW_TRANS_DONE, 0)) //변환 끝
        main.mHandler.sendMessage(Message.obtain(main.mHandler, model.VIEW_RESULT_SET, 0)) //결과 화면 set
        Log.i("OCR", "스레드 끝남")
        vibrator = main.getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createOneShot(2000, 150))
        else
            vibrator.vibrate(500)
    }
}