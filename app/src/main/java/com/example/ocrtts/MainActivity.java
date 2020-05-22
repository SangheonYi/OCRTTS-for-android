package com.example.ocrtts;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

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

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    //Request Code
    private final int PICTURE_REQUEST_CODE = 100;
    private static final int CREATE_REQUEST_CODE = 101;
    private static final int EDIT_REQUEST_CODE = 102;

    //View
    private EditText mEditOcrResult;//변환된 Text View
    private EditText mEditReading_state;//읽는 상태 View
    private EditText mEditOCRprogress;//OCR 진행 상태
    private ImageButton albumButton, mPlayButton, mStopButton, mBeforeButton, mNextButton, mFasterButton, mSlowerButton;
    private SpeedDialView speedDialView;

    //OCR 객체
    static TessBaseAPI sTess;

    private String datapath = "";
    private String lang = "";
    private int OCRIndex = -1;//OCR 진행 중인 이미지 번호
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
    private SAFRW frw;
    private Uri SAFUri;
    private String MIME_TEXT = "text/plain";
    private MyDatabaseOpenHelper mMyDatabaseOpenHelper;
    private String Title = "no title";
    private int Page = 0;
    private String title_last_page = Title + "\nPage: " + Page;
    private boolean isPageUpdated = false;

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

        final Context mainActivityContext = this;
        speedDialView = findViewById(R.id.speedDial);

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

        mTts = new TextToSpeech(mainActivityContext, this);
        sTess = new TessBaseAPI();

        HashMap mTtsMap = new HashMap();
        mTtsMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "unique_id");
        mTts.setSpeechRate((float) readSpeed);

        // Tesseract 인식 언어를 한국어로 설정 및 초기화
        lang = "kor";
        datapath = getFilesDir() + "/tesseract";

        //데이터 관리
        frw = new SAFRW();
        mMyDatabaseOpenHelper = new MyDatabaseOpenHelper(mainActivityContext);

        mMyDatabaseOpenHelper.open();
        mMyDatabaseOpenHelper.create();

        if (checkFile(new File(datapath + "/tessdata"))) {
            sTess.init(datapath, lang);
        }
        mEditOcrResult.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                // 터치 이벤트 제거
                return true;
            }

            ;

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
                                                    intent = frw.createFile(MIME_TEXT, Title);
                                                    startActivityForResult(intent, CREATE_REQUEST_CODE);
                                                    break;

                                                case 1:
                                                    Log.i("frw", "저장 case 1 이어쓰기");
                                                    intent = frw.performFileSearch(MIME_TEXT);
                                                    startActivityForResult(intent, EDIT_REQUEST_CODE);
                                                    break;
                                            }
                                        }
                                    })
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
                if (ReadIndex < bigText.getSize()) {
                    mHandler.sendMessage(Message.obtain(mHandler, 2));
                    mHandler.sendMessage(Message.obtain(mHandler, 3));
                } else {
                    Log.i("띠띠에스", "완독start");
                    mHandler.sendMessage(Message.obtain(mHandler, 4));
                }
                Log.i("띠띠에스", "ReadIndex: " + ReadIndex + " 빅텍 Sentence:  @@" + bigText.getSentence().get(ReadIndex) + "@@");
            }

            @Override
            public void onDone(String utteranceId) {
                CharSum += bigText.getSentence().get(ReadIndex).length();
                Log.i("띠띠에스", "이야 다 시부렸어라");
                mHandler.sendMessage(Message.obtain(mHandler, 2));

                ReadIndex++;
                if (ReadIndex < bigText.getSize())
                    mTts.speak(bigText.getSentence().get(ReadIndex), TextToSpeech.QUEUE_FLUSH, null, "unique_id");
                else {
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
                if (!mTts.isSpeaking() && bigText.getSize() > 0 && !(bigText.getSize() == 1 && bigText.isSentenceNull(0))) {
                    State = "playing";
                    mTts.speak(bigText.getSentence().get(ReadIndex), TextToSpeech.QUEUE_FLUSH, null, "unique_id");
                    mHandler.sendMessage(Message.obtain(mHandler, 6));//버튼 이미지 바꿈
                } else if (State.equals("playing")) {
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
                        CharSum -= bigText.getSentence().get(ReadIndex).length();
                        while (bigText.isSentenceNull(ReadIndex)) {
                            Log.i("띠띠에스", ReadIndex + "번째null인지? : " + bigText.isSentenceNull(ReadIndex));
                            ReadIndex--;
                            CharSum -= bigText.getSentence().get(ReadIndex).length();
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
                    if (ReadIndex < bigText.getSize() - 1) {
                        CharSum += bigText.getSentence().get(ReadIndex).length();
                        ReadIndex++;
                    }
                    Log.i("띠띠에스", "ReadIndex: " + ReadIndex + " 빅텍 사이즈:  " + bigText.getSize());
                    mTts.speak(bigText.getSentence().get(ReadIndex), TextToSpeech.QUEUE_FLUSH, null, "unique_id");
                }
            }
        });

        //앨범 버튼 누르면 갤러리로 들어가서 여러개의 이미지 선택
        albumButton = findViewById(R.id.btn_album);
        albumButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (OCRIndex < 0) {
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    //사진을 여러개 선택할수 있도록 한다
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    intent.setType(android.provider.MediaStore.Images.Media.CONTENT_TYPE);
                    intent.setType("image/*");
                    Log.i("onCreate","album startActivityForResult");
                    startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICTURE_REQUEST_CODE);
                }
            }
        });
    }

    protected synchronized void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i("onActivityResult", "resultCode: " + resultCode);
        if (resultCode == RESULT_OK) {
            Log.i("onActivityResult", "requestCode: " + requestCode);
            if (requestCode == PICTURE_REQUEST_CODE) {
                ClipData clipData = data.getClipData();
                int pickedNumber = 0;
                Log.i("DB", "clipData : " + clipData);
                if (clipData != null) {
                    String pathStr = clipData.getItemAt(0).getUri().getPath();
                    String[] pathArray = pathStr.split("\\/");
                    Log.i("DB", "선택한 이미지 경로 " + pathStr);
                    Title = pathArray[pathArray.length - 2];
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

                } else
                    Log.i("DB", "clipData가 null");
                if (pickedNumber > 0) {
                    OCR thread = new OCR(threadIndex, clipData);// OCR 진행할 스레드
                    threadIndex++;//생성한 스레드 수
                    totalPageNum = pickedNumber - Page;
                    thread.setDaemon(true);
                    thread.start();
                } else {
                    Log.i("DB", "pickedNumber가 0임");
                }
            } else if (requestCode == CREATE_REQUEST_CODE || requestCode == EDIT_REQUEST_CODE) {
                if (data != null) {
                    SAFUri = data.getData();
                    Log.i("onActivityResult", "SAFUri: " + SAFUri);
                } else
                    Log.i("onActivityResult", "data가 null");
            }
        }
        //갤러리 이미지 변환
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
            OCRIndex = 0;
            Log.i("OCR", this.threadNum + "번째 스레드의 run");

            while (Page < clipData.getItemCount()) {
                try {
                    urione = clipData.getItemAt(Page).getUri();
                        /*Log.i("띠띠에스","clipData.getItemAt(tmp).getUri() : " + clipData.getItemAt(tmp).getUri());
                        Log.i("띠띠에스"," Uri.parse(cursor.getString(1)) : " + Uri.parse(cursor.getString(1)));
                        Log.i("띠띠에스"," cursor.getString(1) : " + cursor.getString(1));*/
                    image = MediaStore.Images.Media.getBitmap(getContentResolver(), urione);
                } catch (IOException e) {

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
                OCRresult = stringBuilder.toString();
                mHandler.sendMessage(Message.obtain(mHandler, 1));//결과 화면 set
                if (State.equals("playing"))
                    mHandler.sendMessage(Message.obtain(mHandler, 3));//읽는 중일 시 강조
            }
            Log.i("OCR", "스레드 끝남");
        }
    }

    @SuppressLint("HandlerLeak")
    public Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1://결과화면 set
                    mEditOcrResult.setText(OCRresult);
                    break;

                case 2://읽는 상태 set
                    Reading_State = bigText.getSize() + "문장 중 " + (ReadIndex + 1) + "번째";
                    mEditReading_state.setText(Reading_State);
                    break;

                case 3://읽는 문장 강조
                    Log.i("띠띠에스", ReadIndex + "th 문장 3 강조 들옴 charsum : " + CharSum);
                    mEditOcrResult.requestFocus();
                    if (ReadIndex < bigText.getSize() && (CharSum + bigText.getSentence().get(ReadIndex).length()) <= mEditOcrResult.length()) {
                        Log.i("띠띠에스", ReadIndex + "th 문장 길이 : " + bigText.getSentence().get(ReadIndex).length() + " charsum : " + CharSum);
                        mEditOcrResult.setSelection(CharSum, CharSum + bigText.getSentence().get(ReadIndex).length());
                        Log.i("띠띠에스", ReadIndex + "th 문장 시작 : " + CharSum + " 끝 : " + (CharSum + bigText.getSentence().get(ReadIndex).length()));
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
                    Log.i("띠띠에스", "리셋");
                    break;

                case 5://변환 과정
                    if (OCRIndex < totalPageNum) {
                        mEditOCRprogress.setText(totalPageNum + "장 중 " + OCRIndex + "장 변환");
                        Log.i("띠띠에스", totalPageNum + "장 중 " + OCRIndex + "장 변환");
                    } else {
                        mEditOCRprogress.setText(totalPageNum + "장 Done");
                        mEditOcrResult.append(" ");
                        Log.i("띠띠에스", OCRIndex + "끝?");
                        OCRIndex = -1;
                    }
                    break;

                case 6://버튼 이미지 변환
                    Log.i("띠띠에스", "재생 or 일시정지 State : " + State + " isSpeaking : " + mTts.isSpeaking());
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

    public void setBookTable() {
        if (!isPageUpdated) {
            isPageUpdated = true;
        }

        title_last_page = Title + "\nPage: " + Page;
        mMyDatabaseOpenHelper.open();
        if (threadIndex > 0 && mMyDatabaseOpenHelper.isNewTitle(Title)) {
            if (mMyDatabaseOpenHelper.insertColumn(Title, Page, title_last_page, 0) != -1)
                Log.i("DB", "DB에 삽입됨 : " + Title + "  " + Page);
            else
                Log.i("DB", "DB에 삽입 에러 -1 : " + Title + "  " + Page);
        } else if (threadIndex > 0 && !mMyDatabaseOpenHelper.isNewTitle(Title)) {
            if (mMyDatabaseOpenHelper.updateColumn(mMyDatabaseOpenHelper.getIdByTitle(Title), Title, Page, title_last_page, 0))
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
        Log.i("onDestroy", "onDestroy()");
        if (threadIndex > 0 ) {
            frw.alterDocument(this, OCRresult, SAFUri);
            setBookTable();
        } else
            Log.i("onPause()", "thread is negative");
        sTess.clear();
        sTess.end();
        super.onDestroy();
    }

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
    
}