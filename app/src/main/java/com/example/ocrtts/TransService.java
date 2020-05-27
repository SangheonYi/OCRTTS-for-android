package com.example.ocrtts;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.JobIntentService;

import java.util.Locale;

public class TransService extends JobIntentService {
    private static final String TAG = "TransService";
    private Thread transThread;
    static final int JOB_ID = 1001;

    public TransService(Thread ocrThread) {
        transThread = ocrThread;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
    }

    static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, TransService.class, JOB_ID, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Toast.makeText(this, "Service Started", Toast.LENGTH_LONG).show();
        Log.d(TAG, "onStartCommand()");


    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
