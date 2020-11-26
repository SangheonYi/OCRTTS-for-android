package com.example.ocrtts

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class TransService : Service() {
    var totalPageNum = 0

    //Messenger
    var mActivityMessenger: Messenger? = null
    var serviceHandler = ServiceHandler()
    val mServiceMessenger = Messenger(serviceHandler)

    //View
    var builder: NotificationCompat.Builder? = null
    var notifiManagerCompat: NotificationManagerCompat? = null

    override fun onCreate() {
        Log.d("TransService cycle", "onCreate()")
        super.onCreate()
        startForegroundService()
    }

    fun startForegroundService() {
        notifiManagerCompat = NotificationManagerCompat.from(this)
        builder = if (Build.VERSION.SDK_INT >= 26) {
            val CHANNEL_ID = "ocr_service_channel"
            val channel = NotificationChannel(CHANNEL_ID,
                    "OCR Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            NotificationCompat.Builder(this, CHANNEL_ID)
        } else NotificationCompat.Builder(this)
        builder!!.setSmallIcon(R.mipmap.ic_launcher)
                .setProgress(totalPageNum, 0, false)
                .setContentText("0 / $totalPageNum").color = resources.getColor(R.color.colorPrimary)
        Log.i("TransService cycle", "onStartCommand()")
        Log.i("TransService", "Thread.currentThread().getName()" + Thread.currentThread().name)
        startForeground(notificationId, builder!!.build())
    }

    @SuppressLint("HandlerLeak")
    inner class ServiceHandler : Handler() {
        @SuppressLint("RestrictedApi")
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                CONNECT -> {
                    mActivityMessenger = msg.replyTo
                    Log.i("serviceHandler", "CONNECT to " + msg.replyTo)
                }
                DISCONNECT -> {
                    mActivityMessenger = null
                    Log.i("serviceHandler", "DISCONNECT")
                }
                VIEW_NOTIFI_PROGRESS -> {
                    builder!!.setProgress(totalPageNum, msg.arg1, false)
                            .setContentText(msg.arg1.toString() + " / " + totalPageNum)
                    Log.i("MSG", "service msg arg1 receive: ${msg.arg1}")
                    notifiManagerCompat!!.notify(notificationId, builder!!.build())
                    Log.i("serviceHandler", "VIEW_NOTIFI_PROGRESS: " + totalPageNum + "장 중 " + msg.arg1 + "장 변환")
                }
                VIEW_NOTIFI_DONE -> {
                    notifiManagerCompat!!.cancel(notificationId)
                    Log.i("serviceHandler", "VIEW_TRANS_DONE: " + msg.arg1 + "끝?")
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        totalPageNum = intent.getIntExtra("pageNum", totalPageNum)
        Log.i("TransService cycle", "onBind()")
        return mServiceMessenger.binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("TransService cycle", "onDestroy()")
    }

    companion object {
        const val CONNECT = 7
        const val DISCONNECT = 8
        const val VIEW_NOTIFI_PROGRESS = 9
        const val VIEW_NOTIFI_DONE = 10
        const val notificationId = 13
    }
}