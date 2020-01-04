package com.example.ocrtts;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.example.uriutil.URIUtil;
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

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener{
//dddd
    //View
    private EditText mEditOcrResult;//변환된 Text View
    private EditText mEditReading_state;//읽는 상태 View
    private EditText mEditOCRprogress;//OCR 진행 상태
    private ImageButton albumButton, mPlayButton, mStopButton, mBeforeButton, mNextButton, mFasterButton, mSlowerButton;
    private SpeedDialView speedDialView;
    private EditText editFileName;

    //OCR 객체
    static TessBaseAPI sTess;

    private final int PICTURE_REQUEST_CODE = 100;
    private String datapath = "";
    private String lang = "";
    private int OCRIndex = 0;//OCR 진행 중인 이미지 번호
    private int threadIndex = 0;//thread 시행횟수
    private int totalPageNum = 0;

    //Text 및 TTS 변수
    private TextToSpeech mTts;
    private String OCRresult = " ";//OCR 결과값 받음
    private BigText bigText = new BigText();//Sentence를 가지는 class
    private int ReadIndex = 0;//읽고있는 문장 넘버
    private String State = "stop";//playing, stop
    private int CharSum = 0;//읽고 있는 글자의 view에서의 위치
    private String Reading_State = "현재 문장 " + bigText.getSize() + " 중 " + ReadIndex + "번 문장";
    private static final String TAG = "TextToSpeech";
    private double readSpeed = 3.0; //tts 기본속도 설정

    //데이터 관리
    private AndFileRW frw;
    private URIUtil uriUtil;
    private MyDatabaseOpenHelper mMyDatabaseOpenHelper;
    private String Title = "no title";
    private int Page = 0;
    private String title_last_page = Title + "\nPage: " + Page;
    private boolean isPageUpdated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        editFileName = new EditText(MainActivity.this);
        editFileName.setHint("파일명을 입력하세요.");

        final Context mainActivityContext = this;
        speedDialView = findViewById(R.id.speedDial);

        speedDialView.addActionItem(
                new SpeedDialActionItem.Builder(R.id.fab_write_txt, R.drawable.content_save_outline)
                        .setLabel(getString(R.string.label_fab_write_txt))
                        .setLabelColor(Color.WHITE)
                        .setLabelBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.material_blue_500, getTheme()))
                        .setLabelClickable(false)
                        .setTheme(R.style.AppTheme)
                        .create()
        );
/*        speedDialView.addActionItem(
                new SpeedDialActionItem.Builder(R.id.fab_load_txt, R.drawable.file_import_outline)
                        .setLabel(getString(R.string.label_fab_load_txt))
                        .setLabelColor(Color.WHITE)
                        .setLabelBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.material_blue_500, getTheme()))
                        .setLabelClickable(false)
                        .setTheme(R.style.AppTheme)
                        .create()
        );*/
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

        mTts = new TextToSpeech(mainActivityContext, this);
        sTess = new TessBaseAPI();

        HashMap mTtsMap = new HashMap();
        mTtsMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "unique_id");
        mTts.setSpeechRate((float) readSpeed);

        // Tesseract 인식 언어를 한국어로 설정 및 초기화
        lang = "kor";
        datapath = getFilesDir() + "/tesseract";

        //데이터 관리
        String npath =  Environment.getExternalStorageDirectory().getPath() + "/sdcard/Download/";
        Log.i("pathTest", "Environment.getExternalStorageDirectory().getPath(): " + npath);
        frw = new AndFileRW(npath, OCRresult, "TempSavedFile.txt",true);
        uriUtil = new URIUtil();
        mMyDatabaseOpenHelper = new MyDatabaseOpenHelper(mainActivityContext);

        mMyDatabaseOpenHelper.open();
        mMyDatabaseOpenHelper.create();

        if (checkFile(new File(datapath + "/tessdata"))) {
            sTess.init(datapath, lang);
        }
/*
        mEditOcrResult.setOnTouchListener(new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View view, MotionEvent event)
            {
                // 터치 이벤트 제거
                return true;
            };

        });*/

        speedDialView.setOnActionSelectedListener(new SpeedDialView.OnActionSelectedListener() {
            @Override
            public boolean onActionSelected(SpeedDialActionItem speedDialActionItem) {
                switch (speedDialActionItem.getId()) {
                    case R.id.fab_write_txt:
                        String[] writeOption = {"새로쓰기", "이어쓰기"};
                        int checkedOption = 1;
                        Log.i("fab", "클릭 fab_write_txt");
                        frw.setStr(OCRresult);
                        if (threadIndex>0){
                            //저장 안내
                            Toast.makeText(getApplicationContext(), "파일명을 입력하지 않으면 변환파일의 폴더명으로 저장합니다.", Toast.LENGTH_LONG).show();
                            if (editFileName.getParent() != null)
                                ((ViewGroup) editFileName.getParent()).removeView(editFileName);
                            //대화상자 설정
                            MaterialAlertDialogBuilder writeMADB = new MaterialAlertDialogBuilder(mainActivityContext);
                            writeMADB.setTitle("파일 저장")
                                    .setView(editFileName)
                                    .setSingleChoiceItems(writeOption, checkedOption, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            switch (which){
                                                case 0:
                                                    Log.i("frw", "저장 case 0" );
                                                    frw.setAppendBool(false);
                                                    break;

                                                case  1:
                                                    Log.i("frw", "저장 case 1");
                                                    frw.setAppendBool(true);
                                                    break;

                                                default:
                                                    break;
                                            }
                                        }
                                    })
                                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            String inputTitle = editFileName.getText().toString() + ".txt";
                                            if (!inputTitle.equals(".txt"))
                                                frw.setFileName(inputTitle);
                                            Log.i("frw", "파일이름 " + frw.getFile_name() + "로 결정");
                                            if (frw.strToTxt()){
                                                Toast.makeText(getApplicationContext(), "File saved : " + frw.getPath() + frw.getFile_name(), Toast.LENGTH_LONG).show();
                                                setBookTable();
                                            } else {
                                                Toast.makeText(getApplicationContext(), "File save Failed : " + frw.getPath() + frw.getFile_name(), Toast.LENGTH_LONG).show();
                                            }
                                        }
                                    })
                                    .show();
                            //onListItemClick확인해봐
                        }
                        else
                            Toast.makeText(getApplicationContext(), "변환을 먼저 해주세요.", Toast.LENGTH_LONG).show();

                        return false; // true to keep the Speed Dial open

                    case R.id.fab_load_txt:
                        Log.i("fab", "클릭 fab_load_txt");
                        OCRresult = frw.txtToStr();
                        mHandler.sendMessage(Message.obtain(mHandler, 1));//결과화면 set
                        return false;

                    case R.id.fab_DB:
                        Log.i("fab", "클릭 fab_DB");

                        Cursor listDialogCursor = mMyDatabaseOpenHelper.sortColumn("title");
                        listDialogCursor.moveToFirst();

                        new MaterialAlertDialogBuilder(mainActivityContext)
                                .setTitle("변환 기록")
                                .setMultiChoiceItems(listDialogCursor, "check_bool","title_last_page", new DialogInterface.OnMultiChoiceClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which, boolean isChekced) {
                                        which++;
                                        Log.i("fab", isChekced + " <-isChekced  which- > " + which);
                                        Cursor cursor = mMyDatabaseOpenHelper.sortColumn("title");
                                        cursor.move(which);
                                        if (isChekced){
                                            if (mMyDatabaseOpenHelper.updateColumn(cursor.getLong(0), cursor.getString(1), cursor.getInt(2), cursor.getString(3), 1 ))
                                                Log.i("fab", isChekced + " 변환기록 check " + cursor.getInt(4) + "성공");
                                            else
                                                Log.i("fab", isChekced + " 변환기록 check " + cursor.getInt(4) + "실패");
                                        }
                                        else{
                                            if (mMyDatabaseOpenHelper.updateColumn(cursor.getLong(0), cursor.getString(1), cursor.getInt(2), cursor.getString(3), 0 ))
                                                Log.i("fab", isChekced + " 변환기록 check " + cursor.getInt(4) + "성공");
                                            else
                                                Log.i("fab", isChekced + " 변환기록 check " + cursor.getInt(4) + "실패");
                                        }
                                    }
                                })
                                .setPositiveButton("Ok", new DialogInterface.OnClickListener(){
                                    public void onClick(DialogInterface dialog, int which) {
                                        Cursor cursor = mMyDatabaseOpenHelper.sortColumn("title");
                                        while (cursor.moveToNext()){
                                            if (cursor.getInt(4)==1)
                                                mMyDatabaseOpenHelper.deleteTuple(cursor.getInt(0), 0);
                                        }
                                    }
                                })
                                .show();
                        return false;

                    case R.id.fab_flush_edittxt:
                        Log.i("fab", "클릭 fab_flush_edittxt");
                        OCRresult = " ";
                        mHandler.sendMessage(Message.obtain(mHandler, 1));//결과화면 set
                        Toast.makeText(getApplicationContext(), "Text cleared", Toast.LENGTH_LONG).show();
                        return false;

                    default:
                        return false;
                }
            }
        });

        mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {                                           //음성 발성 listener
            @Override
            public void onStart(String utteranceId) {
                Log.i("띠띠에스", ReadIndex + "번째null인지? : " + bigText.isSentenceNull(ReadIndex));
                if (ReadIndex < bigText.getSize()){
                    mHandler.sendMessage(Message.obtain(mHandler, 2));
                    mHandler.sendMessage(Message.obtain(mHandler, 3));
                }
                else {
                    Log.i("띠띠에스", "완독start");
                    mHandler.sendMessage(Message.obtain(mHandler, 4));
                }
                Log.i("띠띠에스", "ReadIndex: " + ReadIndex +" 빅텍 Sentence:  @@" + bigText.getSentence().get(ReadIndex) + "@@");
            }

            @Override
            public void onDone(String utteranceId) {
                CharSum += bigText.getSentence().get(ReadIndex).length();
                Log.i("띠띠에스", "이야 다 시부렸어라");
                mHandler.sendMessage(Message.obtain(mHandler, 2));

                ReadIndex++;
                if (ReadIndex < bigText.getSize())
                    mTts.speak(bigText.getSentence().get(ReadIndex), TextToSpeech.QUEUE_FLUSH, null, "unique_id");
                else{
                    Log.i("띠띠에스", "완독done");
                    mHandler.sendMessage(Message.obtain(mHandler, 4));
                }

            }

            @Override
            public void onError(String utteranceId) {
                Log.e("띠띠에스", "Fuck you");
            }
        });
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            //재생
            @Override
            public void onClick(View v) {
                Log.i("띠띠에스", "재생 버튼");
                if (!mTts.isSpeaking() && bigText.getSize()>0 && !(bigText.getSize()==1 && bigText.isSentenceNull(0))) {
                    State = "playing";
                    mTts.speak(bigText.getSentence().get(ReadIndex), TextToSpeech.QUEUE_FLUSH, null, "unique_id");
                    mHandler.sendMessage(Message.obtain(mHandler, 6));//버튼 이미지 바꿈
                }
                else if (State.equals("playing")){
                    Log.i("띠띠에스", "일시정지 버튼");
                    if (mTts.isSpeaking() && State.equals("playing")) {
                        State = "stop";
                        mTts.stop();
                    }
                    mHandler.sendMessage(Message.obtain(mHandler, 6));//버튼 이미지 바꿈
                }
            }
        });
        mStopButton.setOnClickListener(new View.OnClickListener() {
            //정지
            @Override
            public void onClick(View v) {
                Log.i("띠띠에스", "정지 버튼");
                if (mTts.isSpeaking() && State.equals("playing")) {
                    State = "stop";
                    mTts.stop();
                }

                mHandler.sendMessage(Message.obtain(mHandler, 4));//리셋
            }
        });
        mSlowerButton.setOnClickListener(new View.OnClickListener() {
            //느리게
            @Override
            public void onClick(View v) {
                Log.i("띠띠에스", "느리게 버튼");
                if (mTts.isSpeaking() && State.equals("playing") && readSpeed > 0.4) {
                    readSpeed = readSpeed - 0.5;
                    mTts.setSpeechRate((float) readSpeed);
                    mTts.speak(bigText.getSentence().get(ReadIndex), TextToSpeech.QUEUE_FLUSH, null, "unique_id");
                }
            }
        });
        mFasterButton.setOnClickListener(new View.OnClickListener() {
            //빠르게
            @Override
            public void onClick(View v) {
                Log.i("띠띠에스", "빨리 버튼");
                if (mTts.isSpeaking() && State.equals("playing") && readSpeed < 5) {
                    readSpeed = readSpeed + 0.5;
                    mTts.setSpeechRate((float) readSpeed);
                    mTts.speak(bigText.getSentence().get(ReadIndex), TextToSpeech.QUEUE_FLUSH, null, "unique_id");
                }
            }
        });
        mBeforeButton.setOnClickListener(new View.OnClickListener() {
            //이전 문장
            @Override
            public void onClick(View v) {
                Log.i("띠띠에스", "이전문장 버튼");
                if (mTts.isSpeaking() && State.equals("playing")) {
                    if (ReadIndex > 0) {
                        //CharSum -= bigText.getSentence().get(ReadIndex - 1).length();
                        ReadIndex--;
                        CharSum -= bigText.getSentence().get(ReadIndex ).length();
                        while (bigText.isSentenceNull(ReadIndex)){
                            Log.i("띠띠에스", ReadIndex + "번째null인지? : " + bigText.isSentenceNull(ReadIndex));
                            ReadIndex--;
                            CharSum -= bigText.getSentence().get(ReadIndex ).length();
                        }//while 없으면 높히 곡도를~에서도 갇힘 있으면 편히 잠들어라 에서만 갇힘
                    }
                    mTts.speak(bigText.getSentence().get(ReadIndex), TextToSpeech.QUEUE_FLUSH, null, "unique_id");
                }
            }
        });
        mNextButton.setOnClickListener(new View.OnClickListener() {
            // 다음 문장
            @Override
            public void onClick(View v) {
                Log.i("띠띠에스", "다음문장 버튼");
                if (mTts.isSpeaking() && State.equals("playing")) {
                    if (ReadIndex < bigText.getSize()-1) {
                        CharSum += bigText.getSentence().get(ReadIndex).length();
                        ReadIndex++;
                    }
                    Log.i("띠띠에스", "ReadIndex: " + ReadIndex +" 빅텍 사이즈:  " + bigText.getSize());
                    mTts.speak(bigText.getSentence().get(ReadIndex), TextToSpeech.QUEUE_FLUSH, null, "unique_id");
                }
            }
        });

        //앨범 버튼 누르면 갤러리로 들어가서 여러개의 이미지 선택
        albumButton = findViewById(R.id.btn_album);
        albumButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (OCRIndex > 0){
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    //사진을 여러개 선택할수 있도록 한다
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    intent.setType(android.provider.MediaStore.Images.Media.CONTENT_TYPE);
                    intent.setType("image/*");
                    startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICTURE_REQUEST_CODE);

                }
            }
        });
    }

    protected synchronized void onActivityResult(int requestCode, int resultCode, Intent data) {
        //갤러리 이미지 변환
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICTURE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                int pickedNumber = 0;
                ClipData clipData = data.getClipData();

                Log.i("DB", "clipData : " + clipData);
                if (clipData != null) {
                    String[] pathArray = uriUtil.getRealPathFromURI(this, clipData.getItemAt(0).getUri()).split("\\/");
                    Log.i("DB", "선택한 이미지 경로 " + uriUtil.getRealPathFromURI(this, clipData.getItemAt(0).getUri()));
                    Title = pathArray[pathArray.length - 2];
                    frw.setFileName(Title + ".txt");
                    Page = mMyDatabaseOpenHelper.getContinuePage(Title);
                    Log.i("DB", "선택한 폴더(책 제목) : " + Title);
                    pickedNumber = clipData.getItemCount();

                    if (mMyDatabaseOpenHelper.isNewTitle(Title)) {
                        isPageUpdated = false;
                        Toast.makeText(getApplicationContext(), "변환을 시작합니다.", Toast.LENGTH_LONG).show();
                    } else if (Page < pickedNumber) {
                        isPageUpdated = false;
                        Toast.makeText(getApplicationContext(), "이전 변환에 이어서 변환합니다.", Toast.LENGTH_LONG).show();
                    } else
                        Toast.makeText(getApplicationContext(), "완료한 변환입니다.\n다시 변환을 원할 시 변환 기록을 지워주세요", Toast.LENGTH_LONG).show();

                } else {
                    Log.i("DB", "clipData가 null");
                }

                if (pickedNumber > 0) {
                    OCR thread = new OCR(threadIndex, clipData);// OCR 진행할 스레드
                    threadIndex++;//생성한 스레드 수
                    totalPageNum = pickedNumber - Page;
                    thread.setDaemon(true);
                    thread.start();
                } else {
                    Log.i("DB", "pickedNumber가 0임");
                }
            }
        }
    }

    private class OCR extends Thread {
        private int threadNum;
        private ClipData clipData;

        OCR(int threadNumA, ClipData data) { // 초기화 작업
            this.threadNum = threadNumA;
            this.clipData = data;
        }

        public synchronized void run() {
            Bitmap image = null;//갤러리에서 이미지 받아와
            String transResult;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(OCRresult);
            Uri urione;
            Log.i("OCR",this.threadNum + "번째 스레드의 run");

            while (Page<clipData.getItemCount()){
                try {
                    urione = clipData.getItemAt(Page).getUri();
                        /*Log.i("띠띠에스","clipData.getItemAt(tmp).getUri() : " + clipData.getItemAt(tmp).getUri());
                        Log.i("띠띠에스"," Uri.parse(cursor.getString(1)) : " + Uri.parse(cursor.getString(1)));
                        Log.i("띠띠에스"," cursor.getString(1) : " + cursor.getString(1));*/
                    image = MediaStore.Images.Media.getBitmap(getContentResolver(), urione);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                sTess.setImage(image);
                OCRIndex++;
                Page++;
                Log.i("OCR", "getUTF8Text가 OCR변환 끝나고 값 받을 때 까지 기다림");
                transResult = sTess.getUTF8Text();
                stringBuilder.append(transResult);
                bigText.addSentence(transResult);
                mHandler.sendMessage(Message.obtain(mHandler, 5));//변환 과정
                frw.setStr(transResult);
                frw.strToTxt();
                Log.i("frw", "변환한거 먼저 저장 : " + Title + " Page : " + Page);
                setBookTable();
                OCRresult = stringBuilder.toString();
                mHandler.sendMessage(Message.obtain(mHandler, 1));
                if (State.equals("playing"))
                    mHandler.sendMessage(Message.obtain(mHandler, 3));//읽는 중일 시 강조
            }
            frw.setStr("\ntmpSaved : " + Title);
            frw.strToTxt();
            Log.i("OCR", "스레드 끝남");
        }
    }

    @SuppressLint("HandlerLeak")
    public Handler mHandler = new Handler(){
        public void handleMessage(Message msg){
            switch(msg.what){
                case 1://결과화면 set
                    mEditOcrResult.setText(OCRresult);
                    break;

                case 2://읽는 상태 set
                    Reading_State = bigText.getSize() + "문장 중 " + (ReadIndex+1) + "번째";
                    mEditReading_state.setText(Reading_State);
                    break;

                case 3://읽는 문장 강조
                    Log.i("띠띠에스", ReadIndex + "th 문장 3 강조 들옴 charsum : " + CharSum);
                    mEditOcrResult.requestFocus();
                    if (ReadIndex < bigText.getSize() && (CharSum + bigText.getSentence().get(ReadIndex).length())<= mEditOcrResult.length()){
                        Log.i("띠띠에스", ReadIndex + "th 문장 길이 : " + bigText.getSentence().get(ReadIndex).length() + " charsum : " + CharSum);
                        mEditOcrResult.setSelection(CharSum, CharSum + bigText.getSentence().get(ReadIndex).length());
                        Log.i("띠띠에스", ReadIndex  + "th 문장 시작 : " + CharSum + " 끝 : " + (CharSum + bigText.getSentence().get(ReadIndex).length()));
                    }
                    break;

                case 4://리셋
                    ReadIndex = 0;
                    CharSum = 0;
                    mEditOcrResult.clearFocus();
                    State = "Stop";
                    mTts.stop();
                    mHandler.sendMessage(Message.obtain(mHandler, 2));
                    mHandler.sendMessage(Message.obtain(mHandler, 6));//버튼 이미지 바꿈
                    Log.i("띠띠에스",  "리셋");
                    break;

                case 5://변환 과정

                    if (OCRIndex == 0){
                        mEditOCRprogress.setText(totalPageNum + "장 변환");
                        Log.i("띠띠에스", totalPageNum + "장 중 " + OCRIndex + "Page 변환");
                    }
                    else if(OCRIndex < totalPageNum){
                        mEditOCRprogress.setText(totalPageNum + "장 중 " + OCRIndex + "장 변환");
                        Log.i("띠띠에스", totalPageNum + "장 중 " + OCRIndex + "장 변환");
                    }
                    else {
                        mEditOCRprogress.setText(totalPageNum + "장 Done");
                        mEditOcrResult.append(" ");
                        Log.i("띠띠에스",  OCRIndex + "끝?");
                        OCRIndex = 0;
                    }

                    break;

                case 6://버튼 이미지 변환
                    Log.i("띠띠에스",  "재생 or 일시정지 State : " + State + " isSpeaking : " + mTts.isSpeaking());
                    if (State.equals("playing") && mTts.isSpeaking())
                        mPlayButton.setImageResource(R.drawable.pause_states);
                    else
                        mPlayButton.setImageResource(R.drawable.play_states);
                    break;

                default:
                    break;
            }
        }
    };

    void copyFiles() {
        AssetManager assetMgr = this.getAssets();

        InputStream is;
        OutputStream os;

        try {
            is = assetMgr.open("tessdata/" + lang + ".traineddata");

            String destFile = datapath + "/tessdata/" + lang + ".traineddata";

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

    public void setBookTable(){
        if (!isPageUpdated) {
            isPageUpdated = true;
        }

        title_last_page = Title + "\nPage: " + Page;
        mMyDatabaseOpenHelper.open();
        if (threadIndex>0 && mMyDatabaseOpenHelper.isNewTitle(Title)){
            if (mMyDatabaseOpenHelper.insertColumn( Title, Page, title_last_page, 0 ) != -1)
                Log.i("DB", "DB에 삽입됨 : " + Title + "  " + Page);
            else
                Log.i("DB", "DB에 삽입 에러 -1 : " + Title + "  " + Page);
        }
        else if (threadIndex>0 && !mMyDatabaseOpenHelper.isNewTitle(Title)){
            if (mMyDatabaseOpenHelper.updateColumn(mMyDatabaseOpenHelper.getIdByTitle(Title), Title, Page, title_last_page, 0 ))
                Log.i("DB", "DB 갱신 됨 : " + Title + "  " + Page);
            else
                Log.i("DB", "DB 갱신 실패 updateColumn <= 0 : " + Title + "  " + Page);
        }
    }

    public boolean checkFile(File dir) {
        //디렉토리가 없으면 디렉토리를 만들고 그후에 파일을 카피
        if (!dir.exists() && dir.mkdirs()) {
            copyFiles();
        }
        //디렉토리가 있지만 파일이 없으면 파일카피 진행
        if (dir.exists()) {
            String datafilepath = datapath + "/tessdata/" + lang + ".traineddata";
            File datafile = new File(datafilepath);
            if (!datafile.exists()) {
                copyFiles();
            }
        }
        return true;
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

    public void onDestroy() {
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
        }
        sTess.clear();
        sTess.end();
        super.onDestroy();
    }
}