package com.example.ocrtts

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.googlecode.tesseract.android.TessBaseAPI
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import com.leinardi.android.speeddial.SpeedDialView.OnActionSelectedListener
import java.io.*
import java.util.*

class MainActivity : AppCompatActivity(), OnInitListener, OCRTTSInter, View.OnClickListener {
    var model: OCRTTSModel = OCRTTSModel()
    //View
    private lateinit var mEditOcrResult : EditText //변환된 Text View
    private lateinit var mEditReading_state: EditText //읽는 상태 View
    private lateinit var mEditOCRprogress: EditText //OCR 진행 상태
    private lateinit var albumButton: ImageButton
    private lateinit var mPlayButton: ImageButton
    private lateinit var mStopButton: ImageButton
    private lateinit var mBeforeButton: ImageButton
    private lateinit var mNextButton: ImageButton
    private lateinit var mFasterButton: ImageButton
    private lateinit var mSlowerButton: ImageButton
    private lateinit var speedDialView: SpeedDialView
    private lateinit var mTts: TextToSpeech

    //Data
    var mMyDatabaseOpenHelper: MyDatabaseOpenHelper? = null

    //Communicate
    var mHandler = MainHandler()
    var mServiceMessenger: Messenger? = null
    var mActivityMessenger: Messenger? = null

    @SuppressLint("HandlerLeak")
     inner class MainHandler : Handler() {
        override fun handleMessage(msg: Message) {
            val msgToService: Message
            when (msg.what) {
                OCRTTSInter.VIEW_RESULT_SET -> mEditOcrResult!!.setText(model!!.OCRresult)
                OCRTTSInter.VIEW_READING_STATE -> {
                    model!!.Reading_State = model!!.bigText.size.toString() + "문장 중 " + (model!!.ReadIndex + 1) + "번째"
                    mEditReading_state!!.setText(model!!.Reading_State)
                }
                OCRTTSInter.VIEW_READ_HIGHLIGHT -> {
                    Log.i("띠띠에스", model!!.ReadIndex.toString() + "th 문장 3 강조 들옴 charsum : " + model!!.CharSum)
                    mEditOcrResult!!.requestFocus()
                    if (model!!.ReadIndex < model!!.bigText.size && model!!.CharSum + model!!.bigText.sentence[model!!.ReadIndex].length <= mEditOcrResult!!.length()) {
                        Log.i("띠띠에스", model!!.ReadIndex.toString() + "th 문장 길이 : " + model!!.bigText.sentence[model!!.ReadIndex].length + " charsum : " + model!!.CharSum)
                        mEditOcrResult!!.setSelection(model!!.CharSum, model!!.CharSum + model!!.bigText.sentence[model!!.ReadIndex].length)
                        Log.i("띠띠에스", model!!.ReadIndex.toString() + "th 문장 시작 : " + model!!.CharSum + " 끝 : " + (model!!.CharSum + model!!.bigText.sentence[model!!.ReadIndex].length))
                    }
                }
                OCRTTSInter.VIEW_RESET -> {
                    model!!.ReadIndex = 0
                    model!!.CharSum = 0
                    mEditOcrResult!!.clearFocus()
                    model!!.State = "Stop"
                    mTts!!.stop()
                    mHandler!!.sendMessage(Message.obtain(mHandler, OCRTTSInter.VIEW_READING_STATE, 0))
                    mHandler!!.sendMessage(Message.obtain(mHandler, OCRTTSInter.VIEW_BUTTON_IMG, 0)) //버튼 이미지 바꿈
                    Log.i("띠띠에스", "리셋")
                }
                OCRTTSInter.VIEW_MAIN_PROGRESS -> {
                    try {
                        msgToService = Message.obtain(null, TransService.VIEW_NOTIFI_PROGRESS, model!!.OCRIndex, 0)
                        msgToService.replyTo = mActivityMessenger
                        mServiceMessenger!!.send(msgToService)
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                    }
                    mEditOCRprogress!!.setText(model!!.totalPageNum.toString() + "장 중 " + model!!.OCRIndex + "장 변환")
                    Log.i("띠띠에스", model!!.totalPageNum.toString() + "장 중 " + model!!.OCRIndex + "장 변환")
                }
                OCRTTSInter.VIEW_TRANS_DONE -> {
                    try {
                        msgToService = Message.obtain(null, TransService.VIEW_NOTIFI_DONE, model!!.OCRIndex)
                        msgToService.replyTo = mActivityMessenger
                        mServiceMessenger!!.send(msgToService)
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                    }
                    mEditOCRprogress!!.setText(model!!.totalPageNum.toString() + "장 Done")
                    mEditOcrResult!!.append(" ")
                    Log.i("띠띠에스", model!!.OCRIndex.toString() + "끝?")
                    model!!.OCRIndex = -1
                }
                OCRTTSInter.VIEW_BUTTON_IMG -> {
                    Log.i("띠띠에스", "재생 or 일시정지 State : " + model!!.State + " isSpeaking : " + mTts!!.isSpeaking)
                    if (model!!.State == "playing" && mTts!!.isSpeaking) mPlayButton!!.setImageResource(R.drawable.pause_states) else mPlayButton!!.setImageResource(R.drawable.play_states)
                }
                else -> {
                }
            }
        }
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            mServiceMessenger = Messenger(iBinder)
            try {
                val msg = Message.obtain(null, TransService.CONNECT, 0)
                msg.replyTo = mActivityMessenger
                mServiceMessenger!!.send(msg)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val mainActivityContext: Context = this
        //저장소 권한 확인 및 요청
        Log.i("에헤라", "쓰기 권한 : " + PermissionUtil.checkPermissions(this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        Log.i("에헤라", "읽기 권한 : " + PermissionUtil.checkPermissions(this, Manifest.permission.READ_EXTERNAL_STORAGE))
        if (!(PermissionUtil.checkPermissions(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        && PermissionUtil.checkPermissions(this, Manifest.permission.READ_EXTERNAL_STORAGE))) {
            Log.i("에헤라", "권한 요청하러 들옴")
            PermissionUtil.requestExternalPermissions(this)
        }
        // 뷰 할당
        mEditOcrResult = findViewById(R.id.edit_ocrresult)
        mEditReading_state = findViewById(R.id.ReadingState_bar)
        mEditOCRprogress = findViewById(R.id.OCRprogress_bar)
        mPlayButton = findViewById(R.id.play)
        mStopButton = findViewById(R.id.stop)
        mBeforeButton = findViewById(R.id.before)
        mNextButton = findViewById(R.id.next)
        mFasterButton = findViewById(R.id.faster)
        mSlowerButton = findViewById(R.id.slower)
        albumButton = findViewById(R.id.btn_album)
        mPlayButton.setOnClickListener(this)
        mStopButton.setOnClickListener(this)
        mSlowerButton.setOnClickListener(this)
        mFasterButton.setOnClickListener(this)
        mBeforeButton.setOnClickListener(this)
        mNextButton.setOnClickListener(this)
        albumButton.setOnClickListener(this)
        speedDialView = findViewById(R.id.speedDial)
        speedDialView!!.addActionItem(
                SpeedDialActionItem.Builder(R.id.fab_write_txt, R.drawable.content_save_outline)
                        .setLabel(getString(R.string.label_fab_create_txt))
                        .setLabelColor(Color.WHITE)
                        .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.material_blue_500, theme))
                        .setLabelClickable(false)
                        .setTheme(R.style.AppTheme)
                        .create()
        )
        speedDialView!!.addActionItem(
                SpeedDialActionItem.Builder(R.id.fab_DB, R.drawable.delete_outline)
                        .setLabel(getString(R.string.label_fab_DB))
                        .setLabelColor(Color.WHITE)
                        .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.material_blue_500, theme))
                        .setLabelClickable(false)
                        .setTheme(R.style.AppTheme)
                        .create()
        )
        speedDialView!!.addActionItem(
                SpeedDialActionItem.Builder(R.id.fab_flush_edittxt, R.drawable.refresh)
                        .setLabel(getString(R.string.label_fab_flush_edittxt))
                        .setLabelColor(Color.WHITE)
                        .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.material_blue_500, theme))
                        .setLabelClickable(false)
                        .setTheme(R.style.AppTheme)
                        .create()
        )
        mTts = TextToSpeech(mainActivityContext, this)
        val mTtsMap = HashMap<String, String>()
        mTtsMap[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "unique_id"
        mTts.setSpeechRate(model.readSpeed.toFloat())
        sTess = TessBaseAPI()
        // Tesseract 인식 언어를 한국어로 설정 및 초기화
        model!!.datapath = "$filesDir/tesseract"
        if (checkFile(File(model!!.datapath + "/tessdata"))) {
            sTess!!.init(model!!.datapath, model!!.lang)
        }

        //데이터 관리
        mMyDatabaseOpenHelper = MyDatabaseOpenHelper(mainActivityContext)
        mMyDatabaseOpenHelper!!.open()
        mMyDatabaseOpenHelper!!.create()
        mHandler = MainHandler()
        mActivityMessenger = Messenger(mHandler)
        mEditOcrResult.setOnTouchListener({ view, event -> // 터치 이벤트 제거
            true
        })
        speedDialView.setOnActionSelectedListener(OnActionSelectedListener { speedDialActionItem ->
            when (speedDialActionItem.id) {
                R.id.fab_write_txt -> {
                    val writeOption = arrayOf("파일생성", "이어쓰기")
                    val checkedOption = intArrayOf(1)
                    Log.i("fab", "클릭 fab_write_txt")
                    //대화상자 설정
                    val fileState = TextView(mainActivityContext)
                    if (model!!.frw.getfName() == null) fileState.hint = "저장할 파일이 없습니다." else fileState.text = model!!.frw.getfName()
                    val writeMADB = MaterialAlertDialogBuilder(mainActivityContext)
                    writeMADB.setTitle("파일 저장")
                            .setSingleChoiceItems(writeOption, checkedOption[0]) { dialog, which ->
                                when (which) {
                                    0 -> checkedOption[0] = 0
                                    1 -> checkedOption[0] = 1
                                }
                            }
                            .setPositiveButton("Ok") { dialog, which ->
                                val intent: Intent
                                when (checkedOption[0]) {
                                    0 -> {
                                        Log.i("frw", "저장 case 0 파일생성")
                                        intent = model!!.frw.createFile(model!!.MIME_TEXT, model!!.Title)
                                        startActivityForResult(intent, OCRTTSInter.CREATE_REQUEST_CODE)
                                    }
                                    1 -> {
                                        Log.i("frw", "저장 case 1 이어쓰기")
                                        intent = model!!.frw.performFileSearch(model!!.MIME_TEXT)
                                        startActivityForResult(intent, OCRTTSInter.EDIT_REQUEST_CODE)
                                    }
                                }
                            }
                            .setView(fileState)
                            .show()
                    //onListItemClick확인해봐
                    false // true to keep the Speed Dial open
                }
                R.id.fab_DB -> {
                    Log.i("fab", "클릭 fab_DB")
                    val listDialogCursor = mMyDatabaseOpenHelper!!.sortColumn("title")
                    listDialogCursor.moveToFirst()
                    MaterialAlertDialogBuilder(mainActivityContext)
                            .setTitle("변환 기록")
                            .setMultiChoiceItems(listDialogCursor, "check_bool", "title_last_page") { dialog, which, isChekced ->
                                var which = which
                                which++
                                Log.i("fab", "$isChekced <-isChekced  which- > $which")
                                val cursor = mMyDatabaseOpenHelper!!.sortColumn("title")
                                cursor.move(which)
                                if (isChekced) {
                                    if (mMyDatabaseOpenHelper!!.updateColumn(cursor.getLong(0), cursor.getString(1), cursor.getInt(2).toLong(), cursor.getString(3), 1)) Log.i("fab", isChekced.toString() + " 변환기록 check " + cursor.getInt(4) + "성공") else Log.i("fab", isChekced.toString() + " 변환기록 check " + cursor.getInt(4) + "실패")
                                } else {
                                    if (mMyDatabaseOpenHelper!!.updateColumn(cursor.getLong(0), cursor.getString(1), cursor.getInt(2).toLong(), cursor.getString(3), 0)) Log.i("fab", isChekced.toString() + " 변환기록 check " + cursor.getInt(4) + "성공") else Log.i("fab", isChekced.toString() + " 변환기록 check " + cursor.getInt(4) + "실패")
                                }
                            }
                            .setPositiveButton("Ok") { dialog, which ->
                                val cursor = mMyDatabaseOpenHelper!!.sortColumn("title")
                                while (cursor.moveToNext()) {
                                    if (cursor.getInt(4) == 1) mMyDatabaseOpenHelper!!.deleteTuple(cursor.getInt(0).toLong(), 0)
                                }
                            }
                            .show()
                    false
                }
                R.id.fab_flush_edittxt -> {
                    Log.i("fab", "클릭 fab_flush_edittxt")
                    model!!.OCRresult = " "
                    (mHandler as MainHandler).sendMessage(Message.obtain(mHandler, OCRTTSInter.VIEW_RESULT_SET, 0)) //결과화면 set
                    Toast.makeText(applicationContext, "Text cleared", Toast.LENGTH_LONG).show()
                    false
                }
                else -> false
            }
        })
        Log.i("onCreate()", "Thread.currentThread().getName()" + Thread.currentThread().name)
        mTts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            //음성 발성 listener
            override fun onStart(utteranceId: String) {
                Log.i("띠띠에스", model!!.ReadIndex.toString() + "번째null인지? : " + model!!.bigText.isSentenceNull(model!!.ReadIndex))
                if (model!!.ReadIndex < model!!.bigText.size) {
                    (mHandler as MainHandler).sendMessage(Message.obtain(mHandler, OCRTTSInter.VIEW_READING_STATE, 0))
                    mHandler.sendMessage(Message.obtain(mHandler, OCRTTSInter.VIEW_READ_HIGHLIGHT, 0))
                } else {
                    Log.i("띠띠에스", "완독start")
                    mHandler.sendMessage(Message.obtain(mHandler, OCRTTSInter.VIEW_RESET, 0))
                }
                Log.i("띠띠에스", "ReadIndex: " + model!!.ReadIndex + " 빅텍 Sentence:  @@" + model!!.bigText.sentence[model!!.ReadIndex] + "@@")
            }

            override fun onDone(utteranceId: String) {
                model!!.CharSum += model!!.bigText.sentence[model!!.ReadIndex].length
                Log.i("띠띠에스", "이야 다 시부렸어라")
                (mHandler as MainHandler).sendMessage(Message.obtain(mHandler, OCRTTSInter.VIEW_READING_STATE, 0))
                model!!.ReadIndex++
                if (model!!.ReadIndex < model!!.bigText.size) mTts!!.speak(model!!.bigText.sentence[model!!.ReadIndex], TextToSpeech.QUEUE_FLUSH, null, "unique_id") else {
                    Log.i("띠띠에스", "완독done")
                    (mHandler as MainHandler).sendMessage(Message.obtain(mHandler, OCRTTSInter.VIEW_RESET, 0))
                }
            }

            override fun onError(utteranceId: String) {
                Log.e("띠띠에스", "Fuck you")
            }
        })
    }

    override fun onClick(src: View) {
        when (src.id) {
            R.id.play -> {
                Log.i("버튼", "재생 버튼")
                if (!mTts!!.isSpeaking && model!!.bigText.size > 0 && !(model!!.bigText.size == 1 && model!!.bigText.isSentenceNull(0))) {
                    model!!.State = "playing"
                    mTts!!.speak(model!!.bigText.sentence[model!!.ReadIndex], TextToSpeech.QUEUE_FLUSH, null, "unique_id")
                    mHandler!!.sendMessage(Message.obtain(mHandler, OCRTTSInter.VIEW_BUTTON_IMG, 0)) //버튼 이미지 바꿈
                } else if (model!!.State == "playing") {
                    Log.i("버튼", "일시정지 버튼")
                    if (mTts!!.isSpeaking && model!!.State == "playing") {
                        model!!.State = "stop"
                        mTts!!.stop()
                    }
                    mHandler!!.sendMessage(Message.obtain(mHandler, OCRTTSInter.VIEW_BUTTON_IMG, 0)) //버튼 이미지 바꿈
                }
            }
            R.id.stop -> {
                Log.i("버튼", "정지 버튼")
                if (mTts!!.isSpeaking && model!!.State == "playing") {
                    model!!.State = "stop"
                    mTts!!.stop()
                }
                mHandler!!.sendMessage(Message.obtain(mHandler, OCRTTSInter.VIEW_RESET, 0)) //리셋
            }
            R.id.before -> {
                Log.i("버튼", "이전문장 버튼")
                if (mTts!!.isSpeaking && model!!.State == "playing") {
                    if (model!!.ReadIndex > 0) {
                        model!!.ReadIndex--
                        model!!.CharSum -= model!!.bigText.sentence[model!!.ReadIndex].length
                        while (model!!.bigText.isSentenceNull(model!!.ReadIndex)) {
                            Log.i("버튼", model!!.ReadIndex.toString() + "번째null인지? : " + model!!.bigText.isSentenceNull(model!!.ReadIndex))
                            model!!.ReadIndex--
                            model!!.CharSum -= model!!.bigText.sentence[model!!.ReadIndex].length
                        } //while 없으면 높히 곡도를~에서도 갇힘 있으면 편히 잠들어라 에서만 갇힘
                    }
                    mTts!!.speak(model!!.bigText.sentence[model!!.ReadIndex], TextToSpeech.QUEUE_FLUSH, null, "unique_id")
                }
            }
            R.id.next -> {
                Log.i("버튼", "다음문장 버튼")
                if (mTts!!.isSpeaking && model!!.State == "playing") {
                    if (model!!.ReadIndex < model!!.bigText.size - 1) {
                        model!!.CharSum += model!!.bigText.sentence[model!!.ReadIndex].length
                        model!!.ReadIndex++
                    }
                    Log.i("버튼", "ReadIndex: " + model!!.ReadIndex + " 빅텍 사이즈:  " + model!!.bigText.size)
                    mTts!!.speak(model!!.bigText.sentence[model!!.ReadIndex], TextToSpeech.QUEUE_FLUSH, null, "unique_id")
                }
            }
            R.id.faster -> {
                Log.i("버튼", "빨리 버튼")
                if (mTts!!.isSpeaking && model!!.State == "playing" && model!!.readSpeed < 5) {
                    model!!.readSpeed = model!!.readSpeed + 0.5
                    mTts!!.setSpeechRate(model!!.readSpeed.toFloat())
                    mTts!!.speak(model!!.bigText.sentence[model!!.ReadIndex], TextToSpeech.QUEUE_FLUSH, null, "unique_id")
                }
            }
            R.id.slower -> {
                Log.i("버튼", "느리게 버튼")
                if (mTts!!.isSpeaking && model!!.State == "playing" && model!!.readSpeed > 0.4) {
                    model!!.readSpeed = model!!.readSpeed - 0.5
                    mTts!!.setSpeechRate(model!!.readSpeed.toFloat())
                    mTts!!.speak(model!!.bigText.sentence[model!!.ReadIndex], TextToSpeech.QUEUE_FLUSH, null, "unique_id")
                }
            }
            R.id.btn_album -> {
                Log.i("버튼", "앨범 버튼")
                if (model!!.OCRIndex < 0) {
//                    Intent intent = new Intent(Intent.ACTION_PICK);
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    //사진을 여러개 선택할수 있도록 한다
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    intent.type = "image/*"
                    Log.i("onCreate", "album startActivityForResult")
                    startActivityForResult(intent, OCRTTSInter.PICTURE_REQUEST_CODE)
                }
            }
        }
    }

    @Synchronized
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i("onActivityResult", "resultCode: $resultCode")
        if (resultCode == RESULT_OK) {
            Log.i("onActivityResult", "requestCode: $requestCode")
            if (requestCode == OCRTTSInter.PICTURE_REQUEST_CODE) {
                var pickedNumber = 0
                val dataUri = data!!.data
                model!!.clipData = data.clipData
                if (dataUri != null && model!!.clipData == null) {
                    model!!.clipData = ClipData.newUri(contentResolver, "URI", dataUri)
                    Log.i("DB", "clipData : " + model!!.clipData)
                }
                if (model!!.clipData != null) {
                    val proj = arrayOf(MediaStore.Images.Media.RELATIVE_PATH)
                    contentResolver
                            .query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null, null).use { cursor ->
                                if (cursor != null && cursor.moveToFirst()) {
                                    /*
                            String[] colNames;
                            colNames = cursor.getColumnNames();
                            Log.i("DB", "getColumnCount: " + cursor.getColumnCount());
                            for (int i = 0; i < colNames.length; i++)
                            {
                                Log.i("DB", "colNames" + i +" : " + colNames[i] + " : " + cursor.getString(i));
                            }
                            */
                                    model!!.Title = cursor.getString(0).split("/".toRegex()).toTypedArray()[1]
                                }
                            }
                    model!!.Page = mMyDatabaseOpenHelper!!.getContinuePage(model!!.Title)
                    Log.i("DB", "선택한 폴더(책 제목) : " + model!!.Title)
                    pickedNumber = model!!.clipData!!.itemCount
                    if (mMyDatabaseOpenHelper!!.isNewTitle(model!!.Title)) {
                        model!!.isPageUpdated = false
                        Toast.makeText(applicationContext, "변환을 시작합니다.", Toast.LENGTH_LONG).show()
                    } else if (model!!.Page < pickedNumber) {
                        model!!.isPageUpdated = false
                        Toast.makeText(applicationContext, "이전 변환에 이어서 변환합니다.", Toast.LENGTH_LONG).show()
                    } else Toast.makeText(applicationContext, "완료한 변환입니다.\n다시 변환을 원할 시 변환 기록을 지워주세요", Toast.LENGTH_LONG).show()
                } else Log.i("DB", "clipData가 null")
                if (pickedNumber > 0) {
                    model!!.threadIndex++ //생성한 스레드 수
                    model!!.totalPageNum = pickedNumber - model!!.Page
                    val thread = OCR(model!!.threadIndex, model!!.clipData) // OCR 진행할 스레드
                    thread.isDaemon = true
                    thread.start()
                } else Log.i("DB", "pickedNumber가 0임")
            } else if (requestCode == OCRTTSInter.CREATE_REQUEST_CODE || requestCode == OCRTTSInter.EDIT_REQUEST_CODE) {
                if (data != null) {
                    model!!.SAFUri = data.data
                    Log.i("DB", "SAFUri: " + model!!.SAFUri)
                    Log.i("DB", "SAFUri.getPath: " + model!!.SAFUri!!.path)
                    var pathStr = ""
                    Log.i("DB", "uri: " + model!!.SAFUri)
                    contentResolver.query(model!!.SAFUri!!, null, null, null, null).use { cursor ->
                        assert(cursor != null)
                        if (cursor!!.moveToFirst()) {
                            Log.i("DB", "OpenableColumns.DISPLAY_NAME: " + OpenableColumns.DISPLAY_NAME)
                            pathStr = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                            Log.i("DB", "pathStr: $pathStr")
                        }
                    }
                    val pathArray = pathStr.split("/".toRegex()).toTypedArray()
                    Log.i("DB", "선택한 파일 경로 $pathStr")
                    model!!.frw.setfName(pathArray[pathArray.size - 1])
                } else Log.i("onActivityResult", "data가 null")
            }
        }
        //갤러리 이미지 변환
    }

    private inner class OCR  // 초기화 작업
    (private val threadNum: Int, data: ClipData?) : Thread() {
        @Synchronized
        override fun run() {
            var image: Bitmap? = null //갤러리에서 이미지 받아와
            var transResult: String?
            val stringBuilder = StringBuilder()
            stringBuilder.append(model!!.OCRresult)
            var urione: Uri?
            if (model!!.Page < model!!.clipData!!.itemCount) {
                model!!.OCRIndex = 0
                val intent = Intent(applicationContext, TransService::class.java)
                intent.putExtra("pageNum", model!!.totalPageNum)
                model!!.mIsBound = bindService(intent, mConnection, BIND_AUTO_CREATE)
            }
            Log.i("OCR", threadNum.toString() + "번째 스레드의 run")
            while (model!!.Page < model!!.clipData!!.itemCount) {
                try {
                    urione = model!!.clipData!!.getItemAt(model!!.Page).uri
                    image = MediaStore.Images.Media.getBitmap(contentResolver, urione)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                sTess!!.setImage(image)
                model!!.OCRIndex++
                model!!.Page++
                Log.i("OCR", "getUTF8Text가 OCR변환 끝나고 값 받을 때 까지 기다림. 그냥 변환 중이란 얘기")
                transResult = sTess!!.utF8Text
                stringBuilder.append(transResult)
                model!!.bigText.addSentence(transResult)
                if (model!!.OCRIndex < model!!.totalPageNum) mHandler!!.sendMessage(Message.obtain(mHandler, OCRTTSInter.VIEW_MAIN_PROGRESS, 0)) //변환 과정
                else mHandler!!.sendMessage(Message.obtain(mHandler, OCRTTSInter.VIEW_TRANS_DONE, 0)) //변환 과정
                model!!.OCRresult = stringBuilder.toString()
                mHandler!!.sendMessage(Message.obtain(mHandler, OCRTTSInter.VIEW_RESULT_SET, 0)) //결과 화면 set
                if (model!!.State == "playing") mHandler!!.sendMessage(Message.obtain(mHandler, OCRTTSInter.VIEW_READ_HIGHLIGHT, 0)) //읽는 중일 시 강조
            }
            Log.i("OCR", "스레드 끝남")
            termiateService()
        }
    }

    fun setBookTable() {
        if (!model!!.isPageUpdated) {
            model!!.isPageUpdated = true
        }
        model!!.title_last_page = """
               ${model!!.Title}
               Page: ${model!!.Page}
               """.trimIndent()
        mMyDatabaseOpenHelper!!.open()
        if (model!!.threadIndex > 0 && mMyDatabaseOpenHelper!!.isNewTitle(model!!.Title)) {
            if (mMyDatabaseOpenHelper!!.insertColumn(model!!.Title, model!!.Page.toLong(), model!!.title_last_page, 0) != -1L) Log.i("DB", "DB에 삽입됨 : " + model!!.Title + "  " + model!!.Page) else Log.i("DB", "DB에 삽입 에러 -1 : " + model!!.Title + "  " + model!!.Page)
        } else if (model!!.threadIndex > 0 && !mMyDatabaseOpenHelper!!.isNewTitle(model!!.Title)) {
            if (mMyDatabaseOpenHelper!!.updateColumn(mMyDatabaseOpenHelper!!.getIdByTitle(model!!.Title), model!!.Title, model!!.Page.toLong(), model!!.title_last_page, 0)) Log.i("DB", "DB 갱신 됨 : " + model!!.Title + "  " + model!!.Page) else Log.i("DB", "DB 갱신 실패 updateColumn <= 0 : " + model!!.Title + "  " + model!!.Page)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = mTts!!.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(OCRTTSInter.TAG, "TextToSpeech 초기화 에러!")
            } else {
                mPlayButton!!.isEnabled = true
            }
        } else {
            Log.e(OCRTTSInter.TAG, "TextToSpeech 초기화 에러!")
        }
    }

    fun checkFile(dir: File): Boolean {
        //디렉토리가 없으면 디렉토리를 만들고 그후에 파일을 카피
        if (!dir.exists() && dir.mkdirs()) {
            copyFiles()
        }
        //디렉토리가 있지만 파일이 없으면 파일카피 진행
        if (dir.exists()) {
            val datafilepath = model!!.datapath + "/tessdata/" + model!!.lang + ".traineddata"
            val datafile = File(datafilepath)
            if (!datafile.exists()) {
                copyFiles()
            }
        }
        return true
    }

    fun copyFiles() {
        val assetMgr = this.assets
        val `is`: InputStream
        val os: OutputStream
        try {
            `is` = assetMgr.open("tessdata/" + model!!.lang + ".traineddata")
            val destFile = model!!.datapath + "/tessdata/" + model!!.lang + ".traineddata"
            os = FileOutputStream(destFile)
            val buffer = ByteArray(1024)
            var read: Int
            while (`is`.read(buffer).also { read = it } != -1) {
                os.write(buffer, 0, read)
            }
            `is`.close()
            os.flush()
            os.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun termiateService() {
        if (model!!.mIsBound) {
            val msg = Message.obtain(null, TransService.DISCONNECT, 0)
            try {
                mServiceMessenger!!.send(msg)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
            unbindService(mConnection)
            model!!.mIsBound = false
        }
    }

    public override fun onDestroy() {
        termiateService()
        if (mTts != null) {
            mTts!!.stop()
            mTts!!.shutdown()
        }
        Log.i("onDestroy", "onDestroy()")
        if (model!!.threadIndex > 0 && model!!.SAFUri != null) {
            model!!.frw.alterDocument(this, model!!.OCRresult, model!!.SAFUri)
            setBookTable()
        } else Log.i("onPause()", "thread is negative")
        sTess!!.clear()
        sTess!!.end()
        super.onDestroy()
    }

    public override fun onStart() {
        super.onStart()
        Log.i("LifeCycle", "onStart() 호출")
    }

    public override fun onResume() {
        super.onResume()
        Log.i("LifeCycle", "onResume() 호출")
    }

    public override fun onPause() {
        super.onPause()
        Log.i("LifeCycle", "onPause() 호출")
    }

    public override fun onStop() {
        super.onStop()
        Log.i("LifeCycle", "onStop() 호출")
    }

    companion object {
        //OCR
        var sTess: TessBaseAPI? = null
    }
}