package com.sayi.sayiocr

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.sayi.sayiocr.ui.MainActivity

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
            Log.i("OCR", "${f.title} trans")
            f.saverPermit = true
            ocrTrans(f)
            f.uriList.clear()
        }
        vibrator = main.getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createOneShot(2000, 150))
        else vibrator.vibrate(500)
        mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_TRANS_DONE)) //변환 끝
        main.terminateService()
    }

    private fun ocrTrans(folder: FolderMeta) {
        while (folder.page < folder.uriList.size) {
            image = getCapturedImage(folder.uriList[folder.page])
            model.sTess!!.setImage(image)
            model.ocrIndex++
            folder.page++
            transResult = model.sTess!!.utF8Text
            model.bigText.addSentence(transResult)
            //변환 과정
            if (model.ocrIndex < folder.uriList.size)
                main.mHandler.sendMessage(Message.obtain(main.mHandler, model.VIEW_PROGRESS_ING))
            //결과 화면 set
            main.mHandler.sendMessage(Message.obtain(main.mHandler, model.VIEW_RESULT_SET, transResult))
            //읽는 중일 시 강조
            if (model.state == "playing")
                main.mHandler.sendMessage(Message.obtain(main.mHandler, model.VIEW_READ_HIGHLIGHT))
        }
    }

    private fun getCapturedImage(photoUri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT < 28) MediaStore.Images.Media.getBitmap(main.contentResolver, photoUri)
        else ImageDecoder.decodeBitmap(ImageDecoder.createSource(main.contentResolver, photoUri))
                .copy(Bitmap.Config.ARGB_8888, true)
    }
}