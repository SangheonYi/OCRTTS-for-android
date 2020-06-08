

package com.example.ocrtts;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class TransService extends Service {
    static final int CONNECT = 7;
    static final int DISCONNECT = 8;
    static final int VIEW_NOTIFI_PROGRESS = 9;
    static final int VIEW_NOTIFI_DONE = 10;
    static final int notificationId = 13;
    int totalPageNum = 0;

    //Messenger
    Messenger mActivityMessenger;
    serviceHandler serviceHandler = new serviceHandler();
    final Messenger mServiceMessenger = new Messenger(serviceHandler);

    //View
    NotificationCompat.Builder builder;
    NotificationManagerCompat notificationManagerCompat;

    public TransService() {
    }

    @Override
    public void onCreate() {
        Log.d("TransService cycle", "onCreate()");
        super.onCreate();
        startForegroundService();
    }

    void startForegroundService() {
        notificationManagerCompat = NotificationManagerCompat.from(this);

        if (Build.VERSION.SDK_INT >= 26) {
            String CHANNEL_ID = "ocr_service_channel";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "OCR Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
            builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        } else
            builder = new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.mipmap.ic_launcher)
                .setProgress(totalPageNum, 0, false)
                .setContentText("0 / " + totalPageNum)
                .setColor(getResources().getColor(R.color.colorPrimary));
        Log.i("TransService cycle", "onStartCommand()");
        Log.i("TransService", "Thread.currentThread().getName()" + Thread.currentThread().getName());
        startForeground(notificationId, builder.build());
    }

    @SuppressLint("HandlerLeak")
    class serviceHandler extends Handler {
        @SuppressLint("RestrictedApi")
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CONNECT:
                    mActivityMessenger = msg.replyTo;
                    Log.i("serviceHandler", "CONNECT to " + msg.replyTo);
                    break;

                case DISCONNECT:
                    mActivityMessenger = null;
                    Log.i("serviceHandler", "DISCONNECT");
                    break;

                case VIEW_NOTIFI_PROGRESS:
                    builder.setProgress(totalPageNum, msg.arg1, false)
                            .setContentText(msg.arg1 + " / " + totalPageNum);
                    notificationManagerCompat.notify(notificationId, builder.build());
                    Log.i("serviceHandler", "VIEW_NOTIFI_PROGRESS: " + totalPageNum + "장 중 " + msg.arg1 + "장 변환");
                    break;

                case VIEW_NOTIFI_DONE:
                    notificationManagerCompat.cancel(notificationId);
                    Log.i("serviceHandler", "VIEW_TRANS_DONE: " + msg.arg1 + "끝?");
                    break;

                default:
                    break;
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        totalPageNum = intent.getIntExtra("pageNum", totalPageNum);
        Log.i("TransService cycle", "onBind()");
        return mServiceMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("TransService cycle", "onDestroy()");

    }
}
