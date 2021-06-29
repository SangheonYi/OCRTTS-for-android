package com.sayi.sayiocr

import android.os.Handler
import android.os.Message
import android.os.RemoteException
import com.example.sayiocr.R
import com.sayi.sayiocr.ui.MainActivity
import com.sayi.sayiocr.ui.transservice.TransService

class MainHandler(private val mainActivity: MainActivity) : Handler() {
    private val mHandler = this
    private lateinit var msgToService: Message

    override fun handleMessage(msg: Message) {
        val mServiceMessenger = mainActivity.mServiceMessenger
        val mActivityMessenger = mainActivity.mActivityMessenger
        val mTts = mainActivity.mTts
        val model = mainActivity.model
        val views = mainActivity.bindings

        when (msg.what) {
            model.VIEW_RESULT_SET -> {
                views.editOcrResult.append(msg.obj.toString())
            }
            model.VIEW_READING_STATE -> {
                model.readState = model.bigText.size.toString() + "문장 중 " + (model.readIndex + 1) + "번째"
                mainActivity.bindings.readingStateBar.setText(model.readState)
            }
            model.VIEW_READ_HIGHLIGHT -> {
                mainActivity.bindings.editOcrResult.requestFocus()
                if (model.readIndex < model.bigText.size &&
                        model.charSum + model.bigText.sentence[model.readIndex].length <= mainActivity.bindings.editOcrResult.length()) {
                    mainActivity.bindings.editOcrResult.setSelection(model.charSum, model.charSum + model.bigText.sentence[model.readIndex].length)
                }
            }
            model.VIEW_RESET -> {
                model.readIndex = 0
                model.charSum = 0
                mainActivity.bindings.editOcrResult.clearFocus()
                model.state = "Stop"
                mTts.stop()
                mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_READING_STATE))
                mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_BUTTON_IMG)) //버튼 이미지 바꿈
            }
            model.VIEW_BUTTON_IMG -> {
                if (model.state == "playing" && mTts.isSpeaking) mainActivity.bindings.play.setImageResource(R.drawable.pause_states)
                else mainActivity.bindings.play.setImageResource(R.drawable.play_states)
            }
            model.VIEW_PROGRESS_ING -> {
                try {
                    msgToService = Message.obtain(null, TransService.VIEW_NOTIFI_PROGRESS, model.ocrIndex)
                    msgToService.replyTo = mActivityMessenger
                    if (mServiceMessenger != null) mServiceMessenger!!.send(msgToService)
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
                mainActivity.bindings.ocrProgressBar.setText("${model.folderTotalPage} 장 중 ${model.ocrIndex} 장 변환")
            }
            model.VIEW_TRANS_DONE -> {
                try {
                    msgToService = Message.obtain(null, TransService.VIEW_NOTIFI_DONE, model.folderTotalPage)
                    msgToService.replyTo = mActivityMessenger
                    if (mServiceMessenger != null) mServiceMessenger!!.send(msgToService)
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
                mainActivity.bindings.ocrProgressBar.setText(model.folderTotalPage.toString() + "장 Done")
                mainActivity.bindings.editOcrResult.append(" ")
                model.ocrIndex = -1
                model.folderTotalPage = 0
            }
        }
    }
}