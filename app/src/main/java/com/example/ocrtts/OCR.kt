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

class OCR(inMain: MainActivity)  // 초기화 작업
    : Thread() {
    private var main: MainActivity = inMain
    private val model = main.model
    private val mHandler = main.mHandler
    private val strBuilder = StringBuilder()
    private var transResult: String? = null
    private var urione: Uri? = null
    private lateinit var intent: Intent
    private lateinit var vibrator: Vibrator
    var image: Bitmap? = null //갤러리에서 이미지 받아와

    @Synchronized
    override fun run() {
        for (f in model.folderMetaList) ocrTrans(f)
        vibrator = main.getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createOneShot(2000, 150))
        else vibrator.vibrate(500)
        mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_TRANS_DONE)) //변환 끝
        mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_RESULT_SET)) //결과 화면 set
        Log.i("OCR", "picked folder number : " + model.folderMetaList.size)
        main.terminateService()
    }

    private fun ocrTrans(folder:FolderMeta) {
        strBuilder.append(model.ocrResult)
        if (folder.page < folder.pickedNumber) {
            model.ocrIndex += folder.page
            intent = Intent(main, TransService::class.java).putExtra("pageNum", folder.folderTotalPages)
            model.mIsBound = main.bindService(intent, main.mConnection, AppCompatActivity.BIND_AUTO_CREATE)
        }
        Log.i("OCR", model.threadIndex.toString() + "번째 스레드의 run")
        while (folder.page < folder.pickedNumber) {
            try {
                urione = folder.uriList[folder.page]
                image = MediaStore.Images.Media.getBitmap(main.contentResolver, urione)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            model.sTess!!.setImage(image)
            model.ocrIndex++
            folder.page++
            Log.i("OCR", "getUTF8Text가 OCR변환 끝나고 값 받을 때 까지 기다림. 그냥 변환 중이란 얘기")
            transResult = model.sTess!!.utF8Text
            strBuilder.append(transResult)
            model.bigText.addSentence(transResult)
            Log.i("MSG", "OCR main send")
            if (model.ocrIndex < folder.folderTotalPages)
                main.mHandler.sendMessage(Message.obtain(main.mHandler, model.VIEW_PROGRESS_ING)) //변환 과정
            model.ocrResult = strBuilder.toString()
            main.mHandler.sendMessage(Message.obtain(main.mHandler, model.VIEW_RESULT_SET)) //결과 화면 set
            if (model.state == "playing") main.mHandler.sendMessage(Message.obtain(main.mHandler, model.VIEW_READ_HIGHLIGHT)) //읽는 중일 시 강조
        }
        Log.i("OCR", "스레드 끝남")
    }
}