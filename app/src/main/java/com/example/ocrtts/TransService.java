package com.example.ocrtts;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.Locale;

public class TransService extends Service {
    private static final String TAG = "TransService";
    private Thread transThread;

    public TransService(Thread ocrThread) {
        transThread = ocrThread;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public int onStartCommand(Intent intent, int flags, int startId){
        Toast.makeText(this, "Service Started", Toast.LENGTH_LONG).show();
        Log.d(TAG, "onStartCommand()");
        transThread.start();
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * StopService가 실행될 때 호출된다.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
