package com.example.ocrtts

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.os.*
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.*
import java.util.*

class MainActivity : AppCompatActivity(), OnInitListener, View.OnClickListener {
    private val mainActivity = this

    //Model
    var model: MyModel = MyModel()

    //View
    var views: MyView = MyView()

    //Control
    private var myDBOpenHelper: MyDatabaseOpenHelper? = null
    var mHandler = MainHandler()
    var mServiceMessenger: Messenger? = null
    var mActivityMessenger: Messenger? = null

    //TTS, OCR
    private lateinit var mTts: TextToSpeech

    @SuppressLint("HandlerLeak")
    inner class MainHandler : Handler() {
        override fun handleMessage(msg: Message) {
            val msgToService: Message
            when (msg.what) {
                model.VIEW_RESULT_SET -> views.mEditOcrResult.setText(model.ocrResult)
                model.VIEW_READING_STATE -> {
                    model.readState = model.bigText.size.toString() + "문장 중 " + (model.readIndex + 1) + "번째"
                    views.mEditReadingState.setText(model.readState)
                }
                model.VIEW_READ_HIGHLIGHT -> {
                    Log.i("띠띠에스", model.readIndex.toString() + "th 문장 3 강조 들옴 charsum : " + model.charSum)
                    views.mEditOcrResult.requestFocus()
                    if (model.readIndex < model.bigText.size &&
                            model.charSum + model.bigText.sentence[model.readIndex].length <= views.mEditOcrResult.length()) {
                        Log.i("띠띠에스", model.readIndex.toString() + "th 문장 길이 : " + model.bigText.sentence[model.readIndex].length + " charsum : " + model.charSum)
                        views.mEditOcrResult.setSelection(model.charSum, model.charSum + model.bigText.sentence[model.readIndex].length)
                        Log.i("띠띠에스", model.readIndex.toString() + "th 문장 시작 : " + model.charSum + " 끝 : " + (model.charSum + model.bigText.sentence[model.readIndex].length))
                    }
                }
                model.VIEW_RESET -> {
                    model.readIndex = 0
                    model.charSum = 0
                    views.mEditOcrResult.clearFocus()
                    model.state = "Stop"
                    mTts.stop()
                    mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_READING_STATE, 0))
                    mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_BUTTON_IMG, 0)) //버튼 이미지 바꿈
                    Log.i("띠띠에스", "리셋")
                }
                model.VIEW_PROGRESS_ING -> {
                    try {
                        msgToService = Message.obtain(null, TransService.VIEW_NOTIFI_PROGRESS, model.ocrIndex, 0)
                        msgToService.replyTo = mActivityMessenger
                        mServiceMessenger!!.send(msgToService)
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                    }
                    views.mEditOCRProgress.setText(model.totalPageNum.toString() + "장 중 " + model.ocrIndex + "장 변환")
                    Log.i("띠띠에스", model.totalPageNum.toString() + "장 중 " + model.ocrIndex + "장 변환")
                }
                model.VIEW_TRANS_DONE -> {
                    try {
                        msgToService = Message.obtain(null, TransService.VIEW_NOTIFI_DONE, model.ocrIndex)
                        msgToService.replyTo = mActivityMessenger
                        mServiceMessenger!!.send(msgToService)
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                    }
                    views.mEditOCRProgress.setText(model.totalPageNum.toString() + "장 Done")
                    views.mEditOcrResult.append(" ")
                    Log.i("띠띠에스", model.ocrIndex.toString() + "끝?")
                    model.ocrIndex = -1
                }
                model.VIEW_BUTTON_IMG -> {
                    Log.i("띠띠에스", "재생 or 일시정지 State : " + model.state + " isSpeaking : " + mTts.isSpeaking)
                    if (model.state == "playing" && mTts.isSpeaking) views.mPlayButton.setImageResource(R.drawable.pause_states)
                    else views.mPlayButton.setImageResource(R.drawable.play_states)
                }
            }
        }
    }

    val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            val msg: Message

            mServiceMessenger = Messenger(iBinder)
            mainActivity.mHandler.sendMessage(Message.obtain(mainActivity.mHandler, model.VIEW_PROGRESS_ING, 0)) //변환 과정
            try {
                msg = Message.obtain(null, TransService.CONNECT, 0)
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
        //저장소 권한 확인 및 요청
        Log.i("Storage permission", "쓰기 권한 : " + PermissionUtil.checkPermissions(mainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        Log.i("Storage permission", "읽기 권한 : " + PermissionUtil.checkPermissions(mainActivity, Manifest.permission.READ_EXTERNAL_STORAGE))
        if (!(PermissionUtil.checkPermissions(mainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        && PermissionUtil.checkPermissions(mainActivity, Manifest.permission.READ_EXTERNAL_STORAGE))) {
            Log.i("에헤라", "권한 요청하러 들옴")
            PermissionUtil.requestExternalPermissions(mainActivity)
        }
        // 뷰 할당
        views.viewsCreate(mainActivity)

        //TTS
        mTts = TextToSpeech(mainActivity, mainActivity)
        val mTtsMap = HashMap<String, String>()
        mTtsMap[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "unique_id"
        mTts.setSpeechRate(model.readSpeed.toFloat())

        // Tesseract 인식 언어를 한국어로 설정 및 초기화
        model.sTess = TessBaseAPI()
        model.dataPath = "$filesDir/tesseract"
        if (checkFile(File(model.dataPath + "/tessdata")))
            model.sTess!!.init(model.dataPath, model.lang)

        //데이터 관리
        myDBOpenHelper = MyDatabaseOpenHelper(mainActivity)
        myDBOpenHelper!!.open()
        myDBOpenHelper!!.create()
        mHandler = MainHandler()
        mActivityMessenger = Messenger(mHandler)

        // 터치 이벤트 제거
        views.mEditOcrResult.setOnTouchListener { view, event -> true }
        views.speedDialView.setOnActionSelectedListener { speedDialActionItem ->
            when (speedDialActionItem.id) {
                R.id.fab_write_txt -> {
                    val writeOption = arrayOf("파일생성", "이어쓰기")
                    var checkedOption = 1
                    val fileState = TextView(mainActivity)

                    Log.i("fab", "클릭 fab_write_txt")
                    //대화상자 설정
                    if (model.frw.getfName() == null) fileState.hint = "저장할 파일이 없습니다."
                    else fileState.text = model.frw.getfName()
                    views.writeMADB.setTitle("파일 저장")
                            .setSingleChoiceItems(writeOption, checkedOption) { dialog, which ->
                                checkedOption = which
                            }
                            .setPositiveButton("Ok") { dialog, which ->
                                val intent: Intent

                                when (checkedOption) {
                                    0 -> {
                                        Log.i("frw", "저장 case 0 파일생성")
                                        intent = model.frw.createFile(model.MIME_TEXT, model.title)
                                        startActivityForResult(intent, model.CREATE_REQUEST_CODE)
                                    }
                                    1 -> {
                                        Log.i("frw", "저장 case 1 이어쓰기")
                                        intent = model.frw.performFileSearch(model.MIME_TEXT)
                                        startActivityForResult(intent, model.EDIT_REQUEST_CODE)
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
                    val listDialogCursor = myDBOpenHelper!!.sortColumn("title")

                    listDialogCursor.moveToFirst()
                    MaterialAlertDialogBuilder(mainActivity)
                            .setTitle("변환 기록")
                            .setMultiChoiceItems(listDialogCursor, "check_bool", "title_last_page") { dialog, picked, isChekced ->
                                var which = picked
                                which++
                                Log.i("fab", "$isChekced <-isChekced  which- > $which")
                                val cursor = myDBOpenHelper!!.sortColumn("title")
                                cursor.move(which)
                                if (isChekced) {
                                    if (myDBOpenHelper!!.updateColumn(cursor.getLong(0), cursor.getString(1), cursor.getInt(2).toLong(), cursor.getString(3), 1))
                                        Log.i("fab", isChekced.toString() + " 변환기록 check " + cursor.getInt(4) + "성공")
                                    else Log.i("fab", isChekced.toString() + " 변환기록 check " + cursor.getInt(4) + "실패")
                                } else {
                                    if (myDBOpenHelper!!.updateColumn(cursor.getLong(0), cursor.getString(1), cursor.getInt(2).toLong(), cursor.getString(3), 0))
                                        Log.i("fab", isChekced.toString() + " 변환기록 check " + cursor.getInt(4) + "성공")
                                    else Log.i("fab", isChekced.toString() + " 변환기록 check " + cursor.getInt(4) + "실패")
                                }
                            }
                            .setPositiveButton("Ok") { dialog, which ->
                                val cursor = myDBOpenHelper!!.sortColumn("title")

                                while (cursor.moveToNext())
                                    if (cursor.getInt(4) == 1) myDBOpenHelper!!.deleteTuple(cursor.getInt(0).toLong(), 0)
                            }
                            .show()
                    false
                }
                R.id.fab_flush_edittxt -> {
                    Log.i("fab", "클릭 fab_flush_edittxt")
                    model.ocrResult = " "
                    mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_RESULT_SET, 0)) //결과화면 set
                    Toast.makeText(applicationContext, "Text cleared", Toast.LENGTH_LONG).show()
                    false
                }
                else -> false
            }
        }
        Log.i("onCreate()", "Thread.currentThread().getName()" + Thread.currentThread().name)
        mTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            //음성 발성 listener
            override fun onStart(utteranceId: String) {
                Log.i("띠띠에스", model.readIndex.toString() + "번째null인지? : " + model.bigText.isSentenceNull(model.readIndex))
                if (model.readIndex < model.bigText.size) {
                    mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_READING_STATE, 0))
                    mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_READ_HIGHLIGHT, 0))
                } else {
                    Log.i("띠띠에스", "완독start")
                    mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_RESET, 0))
                }
                Log.i("띠띠에스", "ReadIndex: " + model.readIndex + " 빅텍 Sentence:  @@" + model.bigText.sentence[model.readIndex] + "@@")
            }

            override fun onDone(utteranceId: String) {
                model.charSum += model.bigText.sentence[model.readIndex].length
                Log.i("띠띠에스", "이야 다 시부렸어라")
                mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_READING_STATE, 0))
                model.readIndex++
                if (model.readIndex < model.bigText.size) mTts.speak(model.bigText.sentence[model.readIndex], TextToSpeech.QUEUE_FLUSH, null, "unique_id") else {
                    Log.i("띠띠에스", "완독done")
                    mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_RESET, 0))
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
                if (!mTts.isSpeaking && model.bigText.size > 0 && !(model.bigText.size == 1 && model.bigText.isSentenceNull(0))) {
                    model.state = "playing"
                    mTts.speak(model.bigText.sentence[model.readIndex], TextToSpeech.QUEUE_FLUSH, null, "unique_id")
                    mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_BUTTON_IMG, 0)) //버튼 이미지 바꿈
                } else if (model.state == "playing") {
                    Log.i("버튼", "일시정지 버튼")
                    if (mTts.isSpeaking && model.state == "playing") {
                        model.state = "stop"
                        mTts.stop()
                    }
                    mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_BUTTON_IMG, 0)) //버튼 이미지 바꿈
                }
            }
            R.id.stop -> {
                Log.i("버튼", "정지 버튼")
                if (mTts.isSpeaking && model.state == "playing") {
                    model.state = "stop"
                    mTts.stop()
                }
                mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_RESET, 0)) //리셋
            }
            R.id.before -> {
                Log.i("버튼", "이전문장 버튼")
                if (mTts.isSpeaking && model.state == "playing") {
                    if (model.readIndex > 0) {
                        model.readIndex--
                        model.charSum -= model.bigText.sentence[model.readIndex].length
                        while (model.bigText.isSentenceNull(model.readIndex)) {
                            Log.i("버튼", model.readIndex.toString() + "번째null인지? : " + model.bigText.isSentenceNull(model.readIndex))
                            model.readIndex--
                            model.charSum -= model.bigText.sentence[model.readIndex].length
                        } //while 없으면 높히 곡도를~에서도 갇힘 있으면 편히 잠들어라 에서만 갇힘
                    }
                    mTts.speak(model.bigText.sentence[model.readIndex], TextToSpeech.QUEUE_FLUSH, null, "unique_id")
                }
            }
            R.id.next -> {
                Log.i("버튼", "다음문장 버튼")
                if (mTts.isSpeaking && model.state == "playing") {
                    if (model.readIndex < model.bigText.size - 1) {
                        model.charSum += model.bigText.sentence[model.readIndex].length
                        model.readIndex++
                    }
                    Log.i("버튼", "ReadIndex: " + model.readIndex + " 빅텍 사이즈:  " + model.bigText.size)
                    mTts.speak(model.bigText.sentence[model.readIndex], TextToSpeech.QUEUE_FLUSH, null, "unique_id")
                }
            }
            R.id.faster -> {
                Log.i("버튼", "빨리 버튼")
                if (mTts.isSpeaking && model.state == "playing" && model.readSpeed < 5) {
                    model.readSpeed = model.readSpeed + 0.5
                    mTts.setSpeechRate(model.readSpeed.toFloat())
                    mTts.speak(model.bigText.sentence[model.readIndex], TextToSpeech.QUEUE_FLUSH, null, "unique_id")
                }
            }
            R.id.slower -> {
                Log.i("버튼", "느리게 버튼")
                if (mTts.isSpeaking && model.state == "playing" && model.readSpeed > 0.4) {
                    model.readSpeed = model.readSpeed - 0.5
                    mTts.setSpeechRate(model.readSpeed.toFloat())
                    mTts.speak(model.bigText.sentence[model.readIndex], TextToSpeech.QUEUE_FLUSH, null, "unique_id")
                }
            }
            R.id.btn_album -> {
                Log.i("버튼", "앨범 버튼")
                if (model.ocrIndex < 0) albumClick()
            }
        }
    }

    private fun albumClick() {
        val writeOption = arrayOf("직접 선택(680장 이하)", "폴더 단위로 변환")
        var checkedOption = 1

        Log.i("fab", "클릭 fab_write_txt")
        //대화상자 설정
        views.albumMADB.setTitle("이미지 가져오기")
                .setSingleChoiceItems(writeOption, checkedOption)
                { dialog, which -> checkedOption = which }
                .setPositiveButton("Ok") { dialog, which ->
                    when (checkedOption) {
                        0 -> {
                            // Intent 한계 용량이 100kb다. 초과 시 binding 오류 발생
                            // ACTION_OPEN_DOCUMENT는 문서에 대한 지속적 장기적 액세스 권한을 받음. 사진 편집 등
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                            // ACTION_GET_CONTENT는 데이터 사본을 가져온다.
                            // val intent = Intent(Intent.ACTION_GET_CONTENT)
                            //사진을 여러개 선택할수 있도록 한다
                            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            intent.type = "image/*"
                            Log.i("onCreate", "album startActivityForResult")
                            startActivityForResult(intent, model.PICTURE_REQUEST_CODE)
                        }
                        1 -> pickFolders()
                    }
                }
                .show()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun pickFolders() {
        var projection = arrayOf(MediaStore.Images.ImageColumns.RELATIVE_PATH)
        var cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, null)
        val folderList = ArrayList<String>()
        val pickedFolder = ArrayList<String>()
        val checkBool: BooleanArray

        if (cursor != null) {
            while (cursor.moveToNext()) {
                if (!folderList.contains(cursor.getString(0))) {
                    Log.i("앨범", "0 index: " + cursor.getString(0))
                    folderList.add(cursor.getString(0))
                }
            }
            cursor.close()
        }
        Log.i("fab", "클릭 fab_write_txt")
        //대화상자 설정
        checkBool = BooleanArray(folderList.size) { false }
        views.folderMADB.setTitle("폴더 단위로 변환")
                .setMultiChoiceItems(folderList.toTypedArray(), checkBool)
                { dialog, which, isChecked ->
                    if (isChecked && !pickedFolder.contains(folderList[which])) {
                        Log.i("folder pick", folderList[which])
                        pickedFolder.add(folderList[which])
                    }
                }
                .setPositiveButton("Ok") { dialog, which ->
                    Log.i("folder pick", which.toString())
                    for (e in pickedFolder) {
                        //선택한 폴더들의 이미지들 Uri 획득하기
                        projection = arrayOf(MediaStore.Images.ImageColumns._ID, MediaStore.Images.ImageColumns.RELATIVE_PATH)
                        cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                projection,
                                MediaStore.Images.ImageColumns.RELATIVE_PATH + " = ?"
                                , arrayOf(e), model.sortOrder)
                        Log.i("folder pick", "colname " + cursor!!.columnNames.contentToString())
                        while (cursor!!.moveToNext()){
                            Log.i("folder pick", "add: ${cursor!!.getString(cursor!!.getColumnIndex(MediaStore.Images.ImageColumns.RELATIVE_PATH))}")
                            model.uriList.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor!!.getLong(0)))
                        }
                    }
                    onActivityResult(model.FOLDER_REQUEST_CODE, RESULT_OK, Intent())
                }
                .show()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Synchronized
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i("onActivityResult", "resultCode: $resultCode")
        if (resultCode == RESULT_OK) {
            Log.i("onActivityResult", "requestCode: $requestCode")
            if (requestCode == model.PICTURE_REQUEST_CODE ||
                    requestCode == model.FOLDER_REQUEST_CODE) {
                // OCR translate
                var pickedNumber: Int
                val thread: OCR
                val proj = arrayOf(MediaStore.Images.Media.RELATIVE_PATH, "_data")

                // picked image list allocate
                model.allocClipData(requestCode, data, mainActivity)
                // image meta data parsing
                // TODO 폴더 단위 변환이면 OCR에서 매 폴더마다 체크해주자.
                model.page = myDBOpenHelper!!.getContinuePage(model.title)
                Log.i("DB", "선택한 폴더(책 제목) : " + model.title)
                pickedNumber = model.uriList.size
                if (myDBOpenHelper!!.isNewTitle(model.title)) {
                    model.isPageUpdated = false
                    Toast.makeText(applicationContext, "변환을 시작합니다.", Toast.LENGTH_LONG).show()
                } else if (model.page < pickedNumber) {
                    model.isPageUpdated = false
                    Toast.makeText(applicationContext, "이전 변환에 이어서 변환합니다.", Toast.LENGTH_LONG).show()
                } else Toast.makeText(applicationContext, "완료한 변환입니다.\n다시 변환을 원할 시 변환 기록을 지워주세요", Toast.LENGTH_LONG).show()
                if (pickedNumber > 0) {
                    model.threadIndex++ //생성한 스레드 수
                    model.totalPageNum = pickedNumber - model.page
                    thread = OCR(model, mainActivity) // OCR 진행할 스레드
                    thread.isDaemon = true
                    thread.start()
                    terminateService()
                } else Log.i("DB", "pickedNumber가 0임")
            } else if (requestCode == model.CREATE_REQUEST_CODE ||
                    requestCode == model.EDIT_REQUEST_CODE) {
                if (data != null) {
                    var pathStr = ""
                    val pathArray: Array<String>

                    model.safUri = data.data
                    Log.i("DB", "SAFUri: " + model.safUri)
                    Log.i("DB", "SAFUri.getPath: " + model.safUri!!.path)
                    Log.i("DB", "uri: " + model.safUri)
                    contentResolver.query(model.safUri!!, null, null, null, null)
                            .use { cursor ->
                                if (BuildConfig.DEBUG && cursor == null) error("Assertion failed")
                                if (cursor!!.moveToFirst()) {
                                    Log.i("DB", "OpenableColumns.DISPLAY_NAME: " + OpenableColumns.DISPLAY_NAME)
                                    pathStr = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                                    Log.i("DB", "pathStr: $pathStr")
                                }
                            }
                    pathArray = pathStr.split("/".toRegex()).toTypedArray()
                    Log.i("DB", "선택한 파일 경로 $pathStr")
                    model.frw.setfName(pathArray[pathArray.size - 1])
                } else Log.i("onActivityResult", "data가 null")
            }
        }
        //갤러리 이미지 변환
    }

    /*   private inner class OCR  // 초기화 작업
           : Thread() {
           @Synchronized
           override fun run() {
               var image: Bitmap? = null //갤러리에서 이미지 받아와
               var transResult: String?
               val strBuilder = StringBuilder()
               var urione: Uri?

               strBuilder.append(model.ocrResult)
               if (model.page < model.clipData!!.itemCount) {
                   model.ocrIndex = 0
                   val intent = Intent(mainActivity, TransService::class.java)
                   intent.putExtra("pageNum", model.totalPageNum)
                   model.mIsBound = bindService(intent, mConnection, BIND_AUTO_CREATE)
               }
               Log.i("OCR", model.threadIndex.toString() + "번째 스레드의 run")
               while (model.page < model.clipData!!.itemCount) {
                   try {
                       urione = model.clipData!!.getItemAt(model.page).uri
                       image = MediaStore.Images.Media.getBitmap(contentResolver, urione)
                   } catch (e: IOException) {
                       e.printStackTrace()
                   }
                   model.sTess!!.setImage(image)
                   model.ocrIndex++
                   model.page++
                   Log.i("OCR", "getUTF8Text가 OCR변환 끝나고 값 받을 때 까지 기다림. 그냥 변환 중이란 얘기")
                   transResult = model.sTess!!.utF8Text
                   strBuilder.append(transResult)
                   model.bigText.addSentence(transResult)
                   if (model.ocrIndex < model.totalPageNum)
                       mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_MAIN_PROGRESS, 0)) //변환 과정
                   else
                       mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_TRANS_DONE, 0)) //변환 끝
                   model.ocrResult = strBuilder.toString()
                   mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_RESULT_SET, 0)) //결과 화면 set
                   if (model.state == "playing") mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_READ_HIGHLIGHT, 0)) //읽는 중일 시 강조
               }
               Log.i("OCR", "스레드 끝남")
               val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
               if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                   vibrator.vibrate(createOneShot(2000, 150))
               else
                   vibrator.vibrate(500)
               terminateService()
           }
       }
   */
    private fun setBookTable() {
        if (!model.isPageUpdated) model.isPageUpdated = true
        model.titleLastPage = """
               ${model.title}
               Page: ${model.page}
               """.trimIndent()
        myDBOpenHelper!!.open()
        if (model.threadIndex > 0 && myDBOpenHelper!!.isNewTitle(model.title)) {
            if (myDBOpenHelper!!.insertColumn(model.title, model.page.toLong(), model.titleLastPage, 0) != -1L)
                Log.i("DB", "DB에 삽입됨 : " + model.title + "  " + model.page) else Log.i("DB", "DB에 삽입 에러 -1 : " + model.title + "  " + model.page)
        } else if (model.threadIndex > 0 && !myDBOpenHelper!!.isNewTitle(model.title)) {
            if (myDBOpenHelper!!.updateColumn(myDBOpenHelper!!.getIdByTitle(model.title), model.title, model.page.toLong(), model.titleLastPage, 0))
                Log.i("DB", "DB 갱신 됨 : " + model.title + "  " + model.page) else Log.i("DB", "DB 갱신 실패 updateColumn <= 0 : " + model.title + "  " + model.page)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = mTts.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED)
                Log.e(model.TAG, "TextToSpeech 초기화 에러!")
            else views.mPlayButton.isEnabled = true
        } else Log.e(model.TAG, "TextToSpeech 초기화 에러!")
    }

    private fun checkFile(dir: File): Boolean {
        //디렉토리가 없으면 디렉토리를 만들고 그후에 파일을 카피
        if (!dir.exists() && dir.mkdirs()) copyFiles()
        //디렉토리가 있지만 파일이 없으면 파일카피 진행
        if (dir.exists()) {
            val dataFilePath = model.dataPath + "/tessdata/" + model.lang + ".traineddata"
            val dataFile = File(dataFilePath)
            if (!dataFile.exists()) copyFiles()
        }
        return true
    }

    private fun copyFiles() {
        val assetMgr = mainActivity.assets
        val inStream: InputStream
        val os: OutputStream
        try {
            inStream = assetMgr.open("tessdata/" + model.lang + ".traineddata")
            val destFile = model.dataPath + "/tessdata/" + model.lang + ".traineddata"
            os = FileOutputStream(destFile)
            val buffer = ByteArray(1024)
            var read: Int
            while (inStream.read(buffer).also { read = it } != -1) {
                os.write(buffer, 0, read)
            }
            inStream.close()
            os.flush()
            os.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun terminateService() {
        if (model.mIsBound) {
            val msg = Message.obtain(null, TransService.DISCONNECT, 0)
            try {
                mServiceMessenger!!.send(msg)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
            unbindService(mConnection)
            model.mIsBound = false
        }
    }

    public override fun onDestroy() {
        terminateService()
        mTts.stop()
        mTts.shutdown()
        Log.i("onDestroy", "onDestroy()")
        if (model.threadIndex > 0 && model.safUri != null) {
            model.frw.alterDocument(mainActivity, model.ocrResult, model.safUri)
            setBookTable()
        } else Log.i("onPause()", "thread is negative")
        model.sTess!!.clear()
        model.sTess!!.end()
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
}