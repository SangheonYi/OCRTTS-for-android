package com.sayi.sayiocr

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedWriter
import java.io.FileWriter
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class OCR(inMain: MainActivity)  // 초기화 작업
    : Thread() {
    private var main: MainActivity = inMain
    private val model = main.model
    private val mHandler = main.mHandler
    private var transResult: String? = null
    private lateinit var vibrator: Vibrator
    var image: Bitmap? = null //갤러리에서 이미지 받아와

    @kotlin.time.ExperimentalTime
    @Synchronized
    override fun run() {
        Log.i("OCR", model.threadIndex.toString() + "번째 스레드의 run")
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

    @kotlin.time.ExperimentalTime
    private fun ocrTrans(folder: FolderMeta) {
        val remover = FileWriter("${main.filesDir}/test.txt")
        val bufferRemover = BufferedWriter(remover)
        bufferRemover.write("")
        bufferRemover.close()
        while (folder.page < folder.uriList.size) {
            val ocrDuration = measureTime{

            image = getCapturedImage(folder.uriList[folder.page])
            model.sTess!!.setImage(image)
            model.ocrIndex++
            folder.page++
            transResult = model.sTess!!.utF8Text
            model.bigText.addSentence(transResult)
            Log.i("MSG", "OCR trans done $transResult")
            if (model.ocrIndex < folder.uriList.size)
                main.mHandler.sendMessage(Message.obtain(main.mHandler, model.VIEW_PROGRESS_ING)) //변환 과정
            main.mHandler.sendMessage(Message.obtain(main.mHandler, model.VIEW_RESULT_SET, transResult)) //결과 화면 set
            if (model.state == "playing") main.mHandler.sendMessage(Message.obtain(main.mHandler, model.VIEW_READ_HIGHLIGHT)) //읽는 중일 시 강조
            }
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                main.registerReceiver(null, ifilter)
            }
            // 배터리 퍼센트를 알려주는 코드입니다.(소수점으로 표시됩니다.)
            val batteryPct: Float? = batteryStatus?.let { intent ->
                val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                level / scale.toFloat()
            }
            val fileWriter = FileWriter("${main.filesDir}/test.txt", true)
            val bufferWriter = BufferedWriter(fileWriter)
            bufferWriter.write("${model.ocrIndex} took ${ocrDuration.inSeconds.roundToInt()} sec," +
                    " remain battery: ${batteryPct}\n")
            bufferWriter.close()
        }
    }

    private fun getCapturedImage(photoUri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT < 28) MediaStore.Images.Media.getBitmap(main.contentResolver, photoUri)
        else ImageDecoder.decodeBitmap(ImageDecoder.createSource(main.contentResolver, photoUri))
                .copy(Bitmap.Config.ARGB_8888, true)
    }
}