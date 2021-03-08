package com.sayi.sayiocr

import android.os.Handler
import android.os.Message
import android.os.RemoteException
import android.util.Log
import com.example.sayiocr.R

class MainHandler(private val mainActivity: MainActivity) : Handler() {
    private val mHandler = this
    private lateinit var msgToService: Message

    override fun handleMessage(msg: Message) {
        val mServiceMessenger = mainActivity.mServiceMessenger
        val mActivityMessenger = mainActivity.mActivityMessenger
        val mTts = mainActivity.mTts
        val model = mainActivity.model
        val views = mainActivity.views

        when (msg.what) {
            model.VIEW_RESULT_SET -> {
                views.mEditOcrResult.append(msg.obj.toString())
            }
            model.VIEW_READING_STATE -> {
                model.readState = model.bigText.size.toString() + "문장 중 " + (model.readIndex + 1) + "번째"
                views.mEditReadingState.setText(model.readState)
            }
            model.VIEW_READ_HIGHLIGHT -> {
                views.mEditOcrResult.requestFocus()
                if (model.readIndex < model.bigText.size &&
                        model.charSum + model.bigText.sentence[model.readIndex].length <= views.mEditOcrResult.length()) {
                    views.mEditOcrResult.setSelection(model.charSum, model.charSum + model.bigText.sentence[model.readIndex].length)
                }
            }
            model.VIEW_RESET -> {
                model.readIndex = 0
                model.charSum = 0
                views.mEditOcrResult.clearFocus()
                model.state = "Stop"
                mTts.stop()
                mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_READING_STATE))
                mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_BUTTON_IMG)) //버튼 이미지 바꿈
            }
            model.VIEW_BUTTON_IMG -> {
                if (model.state == "playing" && mTts.isSpeaking) views.mPlayButton.setImageResource(R.drawable.pause_states)
                else views.mPlayButton.setImageResource(R.drawable.play_states)
            }
            model.VIEW_PROGRESS_ING -> {
                try {
                    msgToService = Message.obtain(null, TransService.VIEW_NOTIFI_PROGRESS, model.ocrIndex)
                    msgToService.replyTo = mActivityMessenger
                    if (mServiceMessenger != null) mServiceMessenger!!.send(msgToService)
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
                views.mEditOCRProgress.setText("${model.folderTotalPage} 장 중 ${model.ocrIndex} 장 변환")
            }
            model.VIEW_TRANS_DONE -> {
                try {
                    msgToService = Message.obtain(null, TransService.VIEW_NOTIFI_DONE, model.folderTotalPage)
                    msgToService.replyTo = mActivityMessenger
                    if (mServiceMessenger != null) mServiceMessenger!!.send(msgToService)
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
                views.mEditOCRProgress.setText(model.folderTotalPage.toString() + "장 Done")
                views.mEditOcrResult.append(" ")
                model.ocrIndex = -1
                model.folderTotalPage = 0
            }
        }
    }
}