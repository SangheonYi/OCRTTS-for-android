package com.example.sayiocr

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Message
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class OCR(inMain: MainActivity)  // 초기화 작업
    : Thread() {
    private var main: MainActivity = inMain
    private val model = main.model
    private val mHandler = main.mHandler
    private var transResult: String? = null
    private lateinit var vibrator: Vibrator
    var image: Bitmap? = null //갤러리에서 이미지 받아와

    @Synchronized
    override fun run() {
        for (f in model.folderMetaList) {
            Log.i("OCR", model.threadIndex.toString() + "번째 스레드의 run")
            model.ocrIndex += f.page
            f.saverPermit = true
            ocrTrans(f)
            f.uriList.clear()
            Log.i("OCR", "스레드 끝남")
        }
        vibrator = main.getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createOneShot(2000, 150))
        else vibrator.vibrate(500)
        mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_TRANS_DONE)) //변환 끝
        Log.i("OCR", "picked folder number : " + model.folderMetaList.size)
        main.terminateService()
    }

    private fun ocrTrans(folder:FolderMeta) {
        while (folder.page < folder.pickedNumber) {
            image = getCapturedImage(folder.uriList[folder.page])
            model.sTess!!.setImage(image)
            model.ocrIndex++
            folder.page++
            Log.i("OCR", "getUTF8Text가 OCR변환 끝나고 값 받을 때 까지 기다림. 그냥 변환 중이란 얘기")
            transResult = model.sTess!!.utF8Text
            model.bigText.addSentence(transResult)
            Log.i("MSG", "OCR trans done $transResult")
            if (model.ocrIndex < folder.folderTotalPages)
                main.mHandler.sendMessage(Message.obtain(main.mHandler, model.VIEW_PROGRESS_ING)) //변환 과정
            main.mHandler.sendMessage(Message.obtain(main.mHandler, model.VIEW_RESULT_SET, transResult)) //결과 화면 set
            if (model.state == "playing") main.mHandler.sendMessage(Message.obtain(main.mHandler, model.VIEW_READ_HIGHLIGHT)) //읽는 중일 시 강조
        }
    }

    private fun getCapturedImage(photoUri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT < 28) MediaStore.Images.Media.getBitmap(main.contentResolver, photoUri)
        else ImageDecoder.decodeBitmap(ImageDecoder.createSource(main.contentResolver, photoUri))
                .copy(Bitmap.Config.ARGB_8888, true)
    }
}