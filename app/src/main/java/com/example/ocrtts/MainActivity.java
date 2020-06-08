package com.example.ocrtts;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.leinardi.android.speeddial.SpeedDialActionItem;
import com.leinardi.android.speeddial.SpeedDialView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener, OCRTTSInter, View.OnClickListener {

    OCRTTSModel model;
    //View
    private EditText mEditOcrResult;//변환된 Text View
    private EditText mEditReading_state;//읽는 상태 View
    private EditText mEditOCRprogress;//OCR 진행 상태
    private ImageButton albumButton, mPlayButton, mStopButton, mBeforeButton, mNextButton, mFasterButton, mSlowerButton;
    private SpeedDialView speedDialView;

    private TextToSpeech mTts;
    //Data
    public MyDatabaseOpenHelper mMyDatabaseOpenHelper;
    //OCR
    static TessBaseAPI sTess;

    //Communicate
    Handler serviceHandler;
    Handler mHandler;
    Messenger mServiceMessenger;
    Messenger mActivityMessenger;

    @SuppressLint("HandlerLeak")
    class mainHandler extends Handler {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case VIEW_RESULT_SET://결과화면 set
                    mEditOcrResult.setText(model.OCRresult);
                    break;

                case VIEW_READING_STATE://읽는 상태 set
                    model.Reading_State = model.bigText.getSize() + "문장 중 " + (model.ReadIndex + 1) + "번째";
                    mEditReading_state.setText(model.Reading_State);
                    break;

                case VIEW_READ_HIGHLIGHT://읽는 문장 강조
                    Log.i("띠띠에스", model.ReadIndex + "th 문장 3 강조 들옴 charsum : " + model.CharSum);
                    mEditOcrResult.requestFocus();
                    if (model.ReadIndex < model.bigText.getSize() && (model.CharSum + model.bigText.getSentence().get(model.ReadIndex).length()) <= mEditOcrResult.length()) {
                        Log.i("띠띠에스", model.ReadIndex + "th 문장 길이 : " + model.bigText.getSentence().get(model.ReadIndex).length() + " charsum : " + model.CharSum);
                        mEditOcrResult.setSelection(model.CharSum, model.CharSum + model.bigText.getSentence().get(model.ReadIndex).length());
                        Log.i("띠띠에스", model.ReadIndex + "th 문장 시작 : " + model.CharSum + " 끝 : " + (model.CharSum + model.bigText.getSentence().get(model.ReadIndex).length()));
                    }
                    break;

                case VIEW_RESET://리셋
                    model.ReadIndex = 0;
                    model.CharSum = 0;
                    mEditOcrResult.clearFocus();
                    model.State = "Stop";
                    mTts.stop();
                    mHandler.sendMessage(Message.obtain(mHandler, VIEW_READING_STATE, 0));
                    mHandler.sendMessage(Message.obtain(mHandler, VIEW_BUTTON_IMG, 0));//버튼 이미지 바꿈
                    Log.i("띠띠에스", "리셋");
                    break;

                case VIEW_MAIN_PROGRESS://변환 과정
                    try {
                        msg = Message.obtain(null, TransService.VIEW_NOTIFI_PROGRESS, model.OCRIndex);
                        msg.replyTo = mActivityMessenger;
                        mServiceMessenger.send(msg);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    mEditOCRprogress.setText(model.totalPageNum + "장 중 " + model.OCRIndex + "장 변환");
                    Log.i("띠띠에스", model.totalPageNum + "장 중 " + model.OCRIndex + "장 변환");
                    break;

                case VIEW_TRANS_DONE://변환 완료
                    try {
                        msg = Message.obtain(null, TransService.VIEW_NOTIFI_DONE, model.OCRIndex);
                        msg.replyTo = mActivityMessenger;
                        mServiceMessenger.send(msg);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    mEditOCRprogress.setText(model.totalPageNum + "장 Done");
                    mEditOcrResult.append(" ");
                    Log.i("띠띠에스", model.OCRIndex + "끝?");
                    model.OCRIndex = -1;
                    break;

                case VIEW_BUTTON_IMG://버튼 이미지 변환
                    Log.i("띠띠에스", "재생 or 일시정지 State : " + model.State + " isSpeaking : " + mTts.isSpeaking());
                    if (model.State.equals("playing") && mTts.isSpeaking())
                        mPlayButton.setImageResource(R.drawable.pause_states);
                    else
                        mPlayButton.setImageResource(R.drawable.play_states);
                    break;

                default:
                    break;
            }
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mServiceMessenger = new Messenger(iBinder);
            try {
                Message msg = Message.obtain(null, TransService.CONNECT, 0);
                msg.replyTo = mActivityMessenger;
                mServiceMessenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Context mainActivityContext = this;
        //저장소 권한 확인 및 요청
        Log.i("에헤라", "쓰기 권한 : " + PermissionUtil.checkPermissions(this, Manifest.permission.WRITE_EXTERNAL_STORAGE));
        Log.i("에헤라", "읽기 권한 : " + PermissionUtil.checkPermissions(this, Manifest.permission.READ_EXTERNAL_STORAGE));
        if (!(PermissionUtil.checkPermissions(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                && PermissionUtil.checkPermissions(this, Manifest.permission.READ_EXTERNAL_STORAGE))) {
            Log.i("에헤라", "권한 요청하러 들옴");
            PermissionUtil.requestExternalPermissions(this);
        }
        // 뷰 할당
        mEditOcrResult = findViewById(R.id.edit_ocrresult);
        mEditReading_state = findViewById(R.id.ReadingState_bar);
        mEditOCRprogress = findViewById(R.id.OCRprogress_bar);

        mPlayButton = findViewById(R.id.play);
        mStopButton = findViewById(R.id.stop);
        mBeforeButton = findViewById(R.id.before);
        mNextButton = findViewById(R.id.next);
        mFasterButton = findViewById(R.id.faster);
        mSlowerButton = findViewById(R.id.slower);
        albumButton = findViewById(R.id.btn_album);
        mPlayButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
        mSlowerButton.setOnClickListener(this);
        mFasterButton.setOnClickListener(this);
        mBeforeButton.setOnClickListener(this);
        mNextButton.setOnClickListener(this);
        albumButton.setOnClickListener(this);

        speedDialView = findViewById(R.id.speedDial);
        if (speedDialView != null) {
            speedDialView.addActionItem(
                    new SpeedDialActionItem.Builder(R.id.fab_write_txt, R.drawable.content_save_outline)
                            .setLabel(getString(R.string.label_fab_create_txt))
                            .setLabelColor(Color.WHITE)
                            .setLabelBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.material_blue_500, getTheme()))
                            .setLabelClickable(false)
                            .setTheme(R.style.AppTheme)
                            .create()
            );
            speedDialView.addActionItem(
                    new SpeedDialActionItem.Builder(R.id.fab_DB, R.drawable.delete_outline)
                            .setLabel(getString(R.string.label_fab_DB))
                            .setLabelColor(Color.WHITE)
                            .setLabelBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.material_blue_500, getTheme()))
                            .setLabelClickable(false)
                            .setTheme(R.style.AppTheme)
                            .create()
            );
            speedDialView.addActionItem(
                    new SpeedDialActionItem.Builder(R.id.fab_flush_edittxt, R.drawable.refresh)
                            .setLabel(getString(R.string.label_fab_flush_edittxt))
                            .setLabelColor(Color.WHITE)
                            .setLabelBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.material_blue_500, getTheme()))
                            .setLabelClickable(false)
                            .setTheme(R.style.AppTheme)
                            .create()
            );
        }
        model = new OCRTTSModel();
        mTts = new TextToSpeech(mainActivityContext, this);

        HashMap mTtsMap = new HashMap();
        mTtsMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "unique_id");
        mTts.setSpeechRate((float) model.readSpeed);

        sTess = new TessBaseAPI();
        // Tesseract 인식 언어를 한국어로 설정 및 초기화
        model.datapath = getFilesDir() + "/tesseract";
        if (checkFile(new File(model.datapath + "/tessdata"))) {
            sTess.init(model.datapath, model.lang);
        }

        //데이터 관리
        mMyDatabaseOpenHelper = new MyDatabaseOpenHelper(mainActivityContext);
        mMyDatabaseOpenHelper.open();
        mMyDatabaseOpenHelper.create();

        mHandler = new mainHandler();
        mActivityMessenger = new Messenger(mHandler);

        mEditOcrResult.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                // 터치 이벤트 제거
                return true;
            }
        });

        speedDialView.setOnActionSelectedListener(new SpeedDialView.OnActionSelectedListener() {
            @Override
            public boolean onActionSelected(SpeedDialActionItem speedDialActionItem) {
                switch (speedDialActionItem.getId()) {
                    case R.id.fab_write_txt:
                        String[] writeOption = {"파일생성", "이어쓰기"};
                        final int[] checkedOption = {1};
                        Log.i("fab", "클릭 fab_write_txt");
                        //대화상자 설정
                        TextView fileState = new TextView(mainActivityContext);
                        if (model.frw.getfName() == null)
                            fileState.setHint("저장할 파일이 없습니다.");
                        else
                            fileState.setText(model.frw.getfName());
                        MaterialAlertDialogBuilder writeMADB = new MaterialAlertDialogBuilder(mainActivityContext);
                        writeMADB.setTitle("파일 저장")
                                .setSingleChoiceItems(writeOption, checkedOption[0], new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        switch (which) {
                                            case 0:
                                                checkedOption[0] = 0;
                                                break;
                                            case 1:
                                                checkedOption[0] = 1;
                                                break;
                                        }
                                    }
                                })
                                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent intent;
                                        switch (checkedOption[0]) {
                                            case 0:
                                                Log.i("frw", "저장 case 0 파일생성");
                                                intent = model.frw.createFile(model.MIME_TEXT, model.Title);
                                                startActivityForResult(intent, CREATE_REQUEST_CODE);
                                                break;
                                            case 1:
                                                Log.i("frw", "저장 case 1 이어쓰기");
                                                intent = model.frw.performFileSearch(model.MIME_TEXT);
                                                startActivityForResult(intent, EDIT_REQUEST_CODE);
                                                break;
                                        }
                                    }
                                })
                                .setView(fileState)
                                .show();
                        //onListItemClick확인해봐
                        return false; // true to keep the Speed Dial open

                    case R.id.fab_DB:
                        Log.i("fab", "클릭 fab_DB");

                        Cursor listDialogCursor = mMyDatabaseOpenHelper.sortColumn("title");
                        listDialogCursor.moveToFirst();

                        new MaterialAlertDialogBuilder(mainActivityContext)
                                .setTitle("변환 기록")
                                .setMultiChoiceItems(listDialogCursor, "check_bool", "title_last_page", new DialogInterface.OnMultiChoiceClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which, boolean isChekced) {
                                        which++;
                                        Log.i("fab", isChekced + " <-isChekced  which- > " + which);
                                        Cursor cursor = mMyDatabaseOpenHelper.sortColumn("title");
                                        cursor.move(which);
                                        if (isChekced) {
                                            if (mMyDatabaseOpenHelper.updateColumn(cursor.getLong(0), cursor.getString(1), cursor.getInt(2), cursor.getString(3), 1))
                                                Log.i("fab", isChekced + " 변환기록 check " + cursor.getInt(4) + "성공");
                                            else
                                                Log.i("fab", isChekced + " 변환기록 check " + cursor.getInt(4) + "실패");
                                        } else {
                                            if (mMyDatabaseOpenHelper.updateColumn(cursor.getLong(0), cursor.getString(1), cursor.getInt(2), cursor.getString(3), 0))
                                                Log.i("fab", isChekced + " 변환기록 check " + cursor.getInt(4) + "성공");
                                            else
                                                Log.i("fab", isChekced + " 변환기록 check " + cursor.getInt(4) + "실패");
                                        }
                                    }
                                })
                                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        Cursor cursor = mMyDatabaseOpenHelper.sortColumn("title");
                                        while (cursor.moveToNext()) {
                                            if (cursor.getInt(4) == 1)
                                                mMyDatabaseOpenHelper.deleteTuple(cursor.getInt(0), 0);
                                        }
                                    }
                                })
                                .show();
                        return false;

                    case R.id.fab_flush_edittxt:
                        Log.i("fab", "클릭 fab_flush_edittxt");
                        model.OCRresult = " ";
                        mHandler.sendMessage(Message.obtain(mHandler, VIEW_RESULT_SET, 0));//결과화면 set
                        Toast.makeText(getApplicationContext(), "Text cleared", Toast.LENGTH_LONG).show();
                        return false;

                    default:
                        return false;
                }
            }
        });
        Log.i("onCreate()", "Thread.currentThread().getName()" + Thread.currentThread().getName());

        mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {                                           //음성 발성 listener
            @Override
            public void onStart(String utteranceId) {
                Log.i("띠띠에스", model.ReadIndex + "번째null인지? : " + model.bigText.isSentenceNull(model.ReadIndex));
                if (model.ReadIndex < model.bigText.getSize()) {
                    mHandler.sendMessage(Message.obtain(mHandler, VIEW_READING_STATE, 0));
                    mHandler.sendMessage(Message.obtain(mHandler, VIEW_READ_HIGHLIGHT, 0));
                } else {
                    Log.i("띠띠에스", "완독start");
                    mHandler.sendMessage(Message.obtain(mHandler, VIEW_RESET, 0));
                }
                Log.i("띠띠에스", "ReadIndex: " + model.ReadIndex + " 빅텍 Sentence:  @@" + model.bigText.getSentence().get(model.ReadIndex) + "@@");
            }

            @Override
            public void onDone(String utteranceId) {
                model.CharSum += model.bigText.getSentence().get(model.ReadIndex).length();
                Log.i("띠띠에스", "이야 다 시부렸어라");
                mHandler.sendMessage(Message.obtain(mHandler, VIEW_READING_STATE, 0));

                model.ReadIndex++;
                if (model.ReadIndex < model.bigText.getSize())
                    mTts.speak(model.bigText.getSentence().get(model.ReadIndex), TextToSpeech.QUEUE_FLUSH, null, "unique_id");
                else {
                    Log.i("띠띠에스", "완독done");
                    mHandler.sendMessage(Message.obtain(mHandler, VIEW_RESET, 0));
                }

            }

            @Override
            public void onError(String utteranceId) {
                Log.e("띠띠에스", "Fuck you");
            }
        });
    }

    public void onClick(View src) {
        switch (src.getId()) {
            case R.id.play:
                Log.i("버튼", "재생 버튼");
                if (!mTts.isSpeaking() && model.bigText.getSize() > 0 && !(model.bigText.getSize() == 1 && model.bigText.isSentenceNull(0))) {
                    model.State = "playing";
                    mTts.speak(model.bigText.getSentence().get(model.ReadIndex), TextToSpeech.QUEUE_FLUSH, null, "unique_id");
                    mHandler.sendMessage(Message.obtain(mHandler, VIEW_BUTTON_IMG, 0));//버튼 이미지 바꿈
                } else if (model.State.equals("playing")) {
                    Log.i("버튼", "일시정지 버튼");
                    if (mTts.isSpeaking() && model.State.equals("playing")) {
                        model.State = "stop";
                        mTts.stop();
                    }
                    mHandler.sendMessage(Message.obtain(mHandler, VIEW_BUTTON_IMG, 0));//버튼 이미지 바꿈
                }
                break;
            case R.id.stop:
                Log.i("버튼", "정지 버튼");
                if (mTts.isSpeaking() && model.State.equals("playing")) {
                    model.State = "stop";
                    mTts.stop();
                }
                mHandler.sendMessage(Message.obtain(mHandler, VIEW_RESET, 0));//리셋
                break;
            case R.id.before:
                Log.i("버튼", "이전문장 버튼");
                if (mTts.isSpeaking() && model.State.equals("playing")) {
                    if (model.ReadIndex > 0) {
                        //CharSum -= bigText.getSentence().get(ReadIndex - 1).length();
                        model.ReadIndex--;
                        model.CharSum -= model.bigText.getSentence().get(model.ReadIndex).length();
                        while (model.bigText.isSentenceNull(model.ReadIndex)) {
                            Log.i("버튼", model.ReadIndex + "번째null인지? : " + model.bigText.isSentenceNull(model.ReadIndex));
                            model.ReadIndex--;
                            model.CharSum -= model.bigText.getSentence().get(model.ReadIndex).length();
                        }//while 없으면 높히 곡도를~에서도 갇힘 있으면 편히 잠들어라 에서만 갇힘
                    }
                    mTts.speak(model.bigText.getSentence().get(model.ReadIndex), TextToSpeech.QUEUE_FLUSH, null, "unique_id");
                }
                break;
            case R.id.next:
                Log.i("버튼", "다음문장 버튼");
                if (mTts.isSpeaking() && model.State.equals("playing")) {
                    if (model.ReadIndex < model.bigText.getSize() - 1) {
                        model.CharSum += model.bigText.getSentence().get(model.ReadIndex).length();
                        model.ReadIndex++;
                    }
                    Log.i("버튼", "ReadIndex: " + model.ReadIndex + " 빅텍 사이즈:  " + model.bigText.getSize());
                    mTts.speak(model.bigText.getSentence().get(model.ReadIndex), TextToSpeech.QUEUE_FLUSH, null, "unique_id");
                }
                break;
            case R.id.faster:
                Log.i("버튼", "빨리 버튼");
                if (mTts.isSpeaking() && model.State.equals("playing") && model.readSpeed < 5) {
                    model.readSpeed = model.readSpeed + 0.5;
                    mTts.setSpeechRate((float) model.readSpeed);
                    mTts.speak(model.bigText.getSentence().get(model.ReadIndex), TextToSpeech.QUEUE_FLUSH, null, "unique_id");
                }
                break;
            case R.id.slower:
                Log.i("버튼", "느리게 버튼");
                if (mTts.isSpeaking() && model.State.equals("playing") && model.readSpeed > 0.4) {
                    model.readSpeed = model.readSpeed - 0.5;
                    mTts.setSpeechRate((float) model.readSpeed);
                    mTts.speak(model.bigText.getSentence().get(model.ReadIndex), TextToSpeech.QUEUE_FLUSH, null, "unique_id");
                }
                break;
            case R.id.btn_album:
                Log.i("버튼", "앨범 버튼");
                if (model.OCRIndex < 0) {
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    //사진을 여러개 선택할수 있도록 한다
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    intent.setType(android.provider.MediaStore.Images.Media.CONTENT_TYPE);
                    intent.setType("image/*");
                    Log.i("onCreate", "album startActivityForResult");
                    startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICTURE_REQUEST_CODE);
                }
                break;
        }
    }

    protected synchronized void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i("onActivityResult", "resultCode: " + resultCode);
        if (resultCode == RESULT_OK) {
            Log.i("onActivityResult", "requestCode: " + requestCode);
            if (requestCode == PICTURE_REQUEST_CODE) {
                model.clipData = data.getClipData();
                int pickedNumber = 0;
                Uri dataUri = data.getData();
                if (dataUri != null && model.clipData == null)
                    model.clipData = ClipData.newUri(getContentResolver(), "URI", dataUri);
                Log.i("DB", "clipData : " + model.clipData);
                if (model.clipData != null) {
                    Uri uri = model.clipData.getItemAt(0).getUri();
                    Log.i("DB", "uri: " + uri);
                    try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst())
                            model.Title = cursor.getString(cursor.getColumnIndex("bucket_display_name"));
                    }
                    model.Page = mMyDatabaseOpenHelper.getContinuePage(model.Title);
                    Log.i("DB", "선택한 폴더(책 제목) : " + model.Title);
                    pickedNumber = model.clipData.getItemCount();
                    if (mMyDatabaseOpenHelper.isNewTitle(model.Title)) {
                        model.isPageUpdated = false;
                        Toast.makeText(getApplicationContext(), "변환을 시작합니다.", Toast.LENGTH_LONG).show();
                    } else if (model.Page < pickedNumber) {
                        model.isPageUpdated = false;
                        Toast.makeText(getApplicationContext(), "이전 변환에 이어서 변환합니다.", Toast.LENGTH_LONG).show();
                    } else
                        Toast.makeText(getApplicationContext(), "완료한 변환입니다.\n다시 변환을 원할 시 변환 기록을 지워주세요", Toast.LENGTH_LONG).show();
                } else
                    Log.i("DB", "clipData가 null");
                if (pickedNumber > 0) {
                    model.threadIndex++;//생성한 스레드 수
                    model.totalPageNum = pickedNumber - model.Page;
                    OCR thread = new OCR(model.threadIndex, model.clipData);// OCR 진행할 스레드
                    thread.setDaemon(true);
                    thread.start();
//                    startService(new Intent(this, TransService.class));

                } else {
                    Log.i("DB", "pickedNumber가 0임");
                }
            } else if (requestCode == CREATE_REQUEST_CODE || requestCode == EDIT_REQUEST_CODE) {
                if (data != null) {
                    model.SAFUri = data.getData();
                    Log.i("DB", "SAFUri: " + model.SAFUri);
                    Log.i("DB", "SAFUri.getPath: " + model.SAFUri.getPath());
                    String pathStr = "";
                    Log.i("DB", "uri: " + model.SAFUri);
                    /*                    for (int i = 0; i < cursor.getColumnCount(); i++)
                        Log.i("DB", "cursor.getColumnName(" + i + "): " + cursor.getColumnName(i));*/
                    try (Cursor cursor = getContentResolver().query(model.SAFUri, null, null, null, null)) {
                        if (cursor.moveToFirst()) {
                            Log.i("DB", "OpenableColumns.DISPLAY_NAME: " + OpenableColumns.DISPLAY_NAME);
                            pathStr = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                            Log.i("DB", "pathStr: " + pathStr);
                        }
                    }
                    String[] pathArray = pathStr.split("/");
                    Log.i("DB", "선택한 파일 경로 " + pathStr);
                    model.frw.setfName(pathArray[pathArray.length - 1]);
                } else
                    Log.i("onActivityResult", "data가 null");
            }
        }
        //갤러리 이미지 변환
    }

    private class OCR extends Thread {
        private int threadNum;

        OCR(int threadNumA, ClipData data) { // 초기화 작업
            this.threadNum = threadNumA;
        }

        public synchronized void run() {
            Bitmap image = null;//갤러리에서 이미지 받아와
            String transResult;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(model.OCRresult);
            Uri urione;
            if (model.Page < model.clipData.getItemCount()) {
                model.OCRIndex = 0;
                Intent intent = new Intent(getApplicationContext(), TransService.class);
                intent.putExtra("pageNum", model.totalPageNum);
                model.mIsBound = bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
                /*if (Build.VERSION.SDK_INT >= 26)
                    startForegroundService(intent);
                else
                    startService(intent);*/
            }
            Log.i("OCR", this.threadNum + "번째 스레드의 run");

            while (model.Page < model.clipData.getItemCount()) {
                try {
                    urione = model.clipData.getItemAt(model.Page).getUri();
                    image = MediaStore.Images.Media.getBitmap(getContentResolver(), urione);
                } catch (IOException e) {

                    e.printStackTrace();
                }
                sTess.setImage(image);
                model.OCRIndex++;
                model.Page++;
                Log.i("OCR", "getUTF8Text가 OCR변환 끝나고 값 받을 때 까지 기다림. 그냥 변환 중이란 얘기");
                transResult = sTess.getUTF8Text();
                stringBuilder.append(transResult);
                model.bigText.addSentence(transResult);
                if (model.OCRIndex < model.totalPageNum) {
                    mHandler.sendMessage(Message.obtain(mHandler, VIEW_MAIN_PROGRESS, 0));//변환 과정
//                    mHandler.sendMessage(Message.obtain(serviceHandler, TransService.VIEW_NOTIFI_PROGRESS, 0));//변환 과정
                } else {
                    mHandler.sendMessage(Message.obtain(mHandler, VIEW_TRANS_DONE, 0));//변환 과정
//                    mHandler.sendMessage(Message.obtain(serviceHandler, TransService.VIEW_NOTIFI_DONE, 0));//변환 과정
                }
                model.OCRresult = stringBuilder.toString();
                mHandler.sendMessage(Message.obtain(mHandler, VIEW_RESULT_SET, 0));//결과 화면 set
                if (model.State.equals("playing"))
                    mHandler.sendMessage(Message.obtain(mHandler, VIEW_READ_HIGHLIGHT, 0));//읽는 중일 시 강조
            }
            Log.i("OCR", "스레드 끝남");
            /*Intent intent = new Intent(getApplicationContext(), TransService.class);
            stopService(intent);*/
            termiateService();
        }
    }

    public void setBookTable() {
        if (!model.isPageUpdated) {
            model.isPageUpdated = true;
        }

        model.title_last_page = model.Title + "\nPage: " + model.Page;
        mMyDatabaseOpenHelper.open();
        if (model.threadIndex > 0 && mMyDatabaseOpenHelper.isNewTitle(model.Title)) {
            if (mMyDatabaseOpenHelper.insertColumn(model.Title, model.Page, model.title_last_page, 0) != -1)
                Log.i("DB", "DB에 삽입됨 : " + model.Title + "  " + model.Page);
            else
                Log.i("DB", "DB에 삽입 에러 -1 : " + model.Title + "  " + model.Page);
        } else if (model.threadIndex > 0 && !mMyDatabaseOpenHelper.isNewTitle(model.Title)) {
            if (mMyDatabaseOpenHelper.updateColumn(mMyDatabaseOpenHelper.getIdByTitle(model.Title), model.Title, model.Page, model.title_last_page, 0))
                Log.i("DB", "DB 갱신 됨 : " + model.Title + "  " + model.Page);
            else
                Log.i("DB", "DB 갱신 실패 updateColumn <= 0 : " + model.Title + "  " + model.Page);
        }
    }

    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = mTts.setLanguage(Locale.KOREAN);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TextToSpeech 초기화 에러!");
            } else {
                mPlayButton.setEnabled(true);
            }
        } else {
            Log.e(TAG, "TextToSpeech 초기화 에러!");
        }

    }

    public boolean checkFile(File dir) {
        //디렉토리가 없으면 디렉토리를 만들고 그후에 파일을 카피
        if (!dir.exists() && dir.mkdirs()) {
            copyFiles();
        }
        //디렉토리가 있지만 파일이 없으면 파일카피 진행
        if (dir.exists()) {
            String datafilepath = model.datapath + "/tessdata/" + model.lang + ".traineddata";
            File datafile = new File(datafilepath);
            if (!datafile.exists()) {
                copyFiles();
            }
        }
        return true;
    }

    void copyFiles() {
        AssetManager assetMgr = this.getAssets();

        InputStream is;
        OutputStream os;

        try {
            is = assetMgr.open("tessdata/" + model.lang + ".traineddata");
            String destFile = model.datapath + "/tessdata/" + model.lang + ".traineddata";
            os = new FileOutputStream(destFile);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            is.close();
            os.flush();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void termiateService(){
        if (model.mIsBound)
        {
            Message msg = Message.obtain(null, TransService.DISCONNECT, 0);
            try {
                mServiceMessenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            unbindService(mConnection);
            model.mIsBound = false;
        }
    }
    
    public void onDestroy() {
        /*Intent intent = new Intent(getApplicationContext(), TransService.class);
        stopService(intent);*/
        termiateService();
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
        }
        Log.i("onDestroy", "onDestroy()");
        if (model.threadIndex > 0 && model.SAFUri != null) {
            model.frw.alterDocument(this, model.OCRresult, model.SAFUri);
            setBookTable();
        } else
            Log.i("onPause()", "thread is negative");
        sTess.clear();
        sTess.end();
        super.onDestroy();
    }

    public void onStart() {
        super.onStart();
        Log.i("LifeCycle", "onStart() 호출");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i("LifeCycle", "onResume() 호출");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i("LifeCycle", "onPause() 호출");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.i("LifeCycle", "onStop() 호출");
    }
}