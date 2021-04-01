package com.sayi.sayiocr

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.sayiocr.R

class TransService : Service() {
    var totalPageNum = 0
    val TAG = "Translated images log DB"

    //Messenger
    var mActivityMessenger: Messenger? = null
    var serviceHandler = ServiceHandler()
    val mServiceMessenger = Messenger(serviceHandler)

    //View
    var builder: NotificationCompat.Builder? = null
    var notifiManagerCompat: NotificationManagerCompat? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    private fun startForegroundService() {
        notifiManagerCompat = NotificationManagerCompat.from(this)
        builder = if (Build.VERSION.SDK_INT >= 26) {
            val CHANNEL_ID = "ocr_service_channel"
            val channel = NotificationChannel(CHANNEL_ID,
                    "OCRTTS notification",
                    NotificationManager.IMPORTANCE_NONE)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            NotificationCompat.Builder(this, CHANNEL_ID)
        } else NotificationCompat.Builder(this)
        builder!!.setSmallIcon(R.mipmap.ic_launcher)
                .setProgress(totalPageNum, 0, false)
                .setContentText("0 / $totalPageNum").color = resources.getColor(R.color.colorPrimary)
        
        
        startForeground(notificationId, builder!!.build())
    }

    @SuppressLint("HandlerLeak")
    inner class ServiceHandler : Handler() {
        @SuppressLint("RestrictedApi")
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                CONNECT -> mActivityMessenger = msg.replyTo
                DISCONNECT -> mActivityMessenger = null
                VIEW_NOTIFI_PROGRESS -> {
                    builder!!.setProgress(totalPageNum, msg.obj.toString().toInt(), false)
                            .setContentText(msg.obj.toString() + " / " + totalPageNum)
                    notifiManagerCompat!!.notify(notificationId, builder!!.build())
                }
                VIEW_NOTIFI_DONE -> {
                    notifiManagerCompat!!.cancel(notificationId)
                    stopForeground(true)
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        totalPageNum = intent.getIntExtra("pageNum", totalPageNum)
        return mServiceMessenger.binder
    }

    companion object {
        const val CONNECT = 7
        const val DISCONNECT = 8
        const val VIEW_NOTIFI_PROGRESS = 9
        const val VIEW_NOTIFI_DONE = 10
        const val notificationId = 13
    }
}