// main activity

package com.sayi.sayiocr

import android.Manifest
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
import androidx.appcompat.app.AppCompatActivity
import com.example.sayiocr.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.googlecode.tesseract.android.TessBaseAPI
import com.leinardi.android.speeddial.SpeedDialView
import java.io.*
import java.util.*

class MainActivity : AppCompatActivity(), OnInitListener, View.OnClickListener {
    private val mainActivity = this

    //Model
    val model: MyModel = MyModel()

    //Control
    val mHandler = MainHandler(mainActivity)

    //View
    val views: MyView = MyView()
    var myDBHelper: MyDatabaseOpenHelper? = null
    lateinit var mServiceMessenger: Messenger
    lateinit var mActivityMessenger: Messenger

    //TTS, OCR
    lateinit var mTts: TextToSpeech

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
        myDBHelper = MyDatabaseOpenHelper(mainActivity)
        myDBHelper!!.open()
        myDBHelper!!.create()
        mActivityMessenger = Messenger(mHandler)

        // 터치 이벤트 제거
        views.mEditOcrResult.setOnTouchListener { view, event -> true }
        views.speedDialView.setOnActionSelectedListener(selectedListener)
        Log.i("onCreate()", "Thread.currentThread().getName()" + Thread.currentThread().name)
        mTts.setOnUtteranceProgressListener(utterListener)
        // 변환 로그
        val file = File("${filesDir}/test.txt")

        val reader = file.bufferedReader()
        val iterator = reader.lineSequence().iterator()

        val content = StringBuffer()
        while(iterator.hasNext()) {
            content.append("${iterator.next()}\n")
        }
        reader.close()
        views.mEditOcrResult.setText(content)
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
                        }
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

    @Synchronized
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i("onActivityResult", "resultCode: $resultCode")
        if (resultCode == RESULT_OK) {
            Log.i("onActivityResult", "requestCode: $requestCode")
            if (requestCode == model.PICTURE_REQUEST_CODE && data != null) {
                // picked image list allocate
                val folder: FolderMeta

                model.folderMetaList.add(FolderMeta())
                folder = model.folderMetaList.first()
                if (data.data != null) {
                    // 이미지 한 장만 선택했을 때
                    folder.uriList.add(data.data!!)
                    Log.i("DB", "clipData : " + folder.uriList)
                } else if (data.clipData != null)
                    for (i in 0 until data.clipData!!.itemCount)
                        folder.uriList.add(data.clipData!!.getItemAt(i).uri)
                model.runOCR(mainActivity)
            }
            else if (requestCode == model.CREATE_REQUEST_CODE || requestCode == model.EDIT_REQUEST_CODE) {
                if (data != null) {
                    var pathStr = ""
                    val pathArray: Array<String>

                    model.safUri = data.data
                    Log.i("DB", "SAFUri: " + model.safUri)
                    Log.i("DB", "SAFUri.getPath: " + model.safUri!!.path)
                    Log.i("DB", "uri: " + model.safUri)
                    contentResolver.query(model.safUri!!, null, null, null, null)
                            .use { cursor ->
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
    }

    // Listener
    private val selectedListener = SpeedDialView.OnActionSelectedListener { speedDialActionItem ->
        when (speedDialActionItem.id) {
            R.id.fab_write_txt -> {
                setFabWrite()
                false // true to keep the Speed Dial open
            }
            R.id.fab_DB -> {
                Log.i("fab", "클릭 fab_DB")
                setFabDB()
                false
            }
            R.id.fab_flush_edittxt -> {
                Log.i("fab", "클릭 fab_flush_edittxt")
                model.ocrResult = " "
                mHandler.sendMessage(Message.obtain(mHandler, model.VIEW_RESULT_SET, model.ocrResult)) //결과화면 set
                Toast.makeText(applicationContext, "Text cleared", Toast.LENGTH_LONG).show()
                false
            }
            else -> false
        }
    }

    private val utterListener = object : UtteranceProgressListener() {
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
    }

    val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            var msg: Message
            val thread = OCR(mainActivity) // OCR 진행할 스레드

            mServiceMessenger = Messenger(iBinder)
            Log.i("MSG", "onServiceConnected main send")
            msg = Message.obtain(null, model.VIEW_PROGRESS_ING, 0)
            mActivityMessenger.send(msg) //변환 과정
            try {
                Log.i("MSG", "onServiceConnected service send")
                msg = Message.obtain(null, TransService.CONNECT, 0)
                msg.replyTo = mActivityMessenger
                if (mServiceMessenger != null) mServiceMessenger.send(msg)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
            model.threadIndex++ //생성한 스레드 수
            thread.isDaemon = true
            thread.start()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {}
    }

    // selectedListener sub method
    private fun setFabWrite() {
        var checkedOption = 1
        val writeOption = arrayOf("파일생성", "이어쓰기")
        val fileState = TextView(mainActivity)
        val myPref = getSharedPreferences(model.PREFS_NAME, MODE_PRIVATE)
        val prefEdit = myPref.edit()

        Log.i("fab", "setFabWrite")
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
                            intent = if (model.folderMetaList.isNotEmpty()) model.frw.createFile(model.MIME_TEXT, model.folderMetaList.first().title)
                            else model.frw.createFile(model.MIME_TEXT, "no title")
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
        if (myPref.getBoolean("save_guide_again", true))
            views.saveHelp.setTitle("도움말")
                    .setMessage("변환된 파일을 저장할 수 있습니다.\n" +
                            "저장할 파일을 선택하거나 새로 생성합니다.\n" +
                            "어플 종료 시 결과가 선택하신 파일에 자동으로 저장됩니다.")
                    .setNeutralButton("더 이상 보지 않기") { dialog, which ->
                        prefEdit.putBoolean(model.SAVE_GUIDE_AGAIN, false)
                        prefEdit.apply()
                    }
                    .setPositiveButton("Ok") { dialog, which ->
                    }
                    .show()
    }

    private fun setFabDB() {
        val listDialogCursor = myDBHelper!!.sortColumn("title")
        val cursor = myDBHelper!!.sortColumn("title")

        listDialogCursor.moveToFirst()
        MaterialAlertDialogBuilder(mainActivity)
                .setTitle("변환 기록")
                .setMultiChoiceItems(listDialogCursor, "check_bool", "title_last_page") { dialog, picked, isChekced ->
                    Log.i("fab", "$isChekced <-isChekced  which- > $picked + 1")
                    cursor.move(picked + 1)
                    if (myDBHelper!!.updateColumn(cursor.getLong(0), cursor.getString(1), cursor.getInt(2).toLong(), cursor.getString(3), 1))
                        Log.i("fab", isChekced.toString() + " 변환기록 check " + cursor.getInt(4) + "성공")
                    else Log.i("fab", isChekced.toString() + " 변환기록 check " + cursor.getInt(4) + "실패")
                }
                .setPositiveButton("Ok") { dialog, which ->
                    while (cursor.moveToNext())
                        if (cursor.getInt(4) == 1) myDBHelper!!.deleteTuple(cursor.getInt(0).toLong(), 0)
                }
                .show()
    }

    // onClick sub method
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

    private fun pickFolders() {
        var projection = arrayOf(model.mediaFolder)
        var cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, null)
        val folderList = ArrayList<String>()
        val pickedFolder = ArrayList<String>()
        val checkBool: BooleanArray
        var folder: FolderMeta

        while (cursor!!.moveToNext()) {
            if (!folderList.contains(cursor.getString(0))) {
                Log.i("앨범", "0 index: " + cursor.getString(0))
                folderList.add(cursor.getString(0))
            }
        }
        cursor.close()
        Log.i("fab", "클릭 fab_write_txt")
        //대화상자 설정
        checkBool = BooleanArray(folderList.size) { false }
        views.folderMADB.setTitle("폴더 단위로 변환")
                .setMultiChoiceItems(folderList.toTypedArray(), checkBool) { dialog, which, isChecked ->
                    if (isChecked && !pickedFolder.contains(folderList[which])) pickedFolder.add(folderList[which])
                    else if (!isChecked && pickedFolder.contains(folderList[which])) pickedFolder.remove(folderList[which])
                }
                .setPositiveButton("Ok") { dialog, which ->
                    Log.i("folder pick", which.toString())
                    for (e in pickedFolder) {
                        //선택한 폴더들의 이미지들 Uri 획득하기
                        model.folderMetaList.add(FolderMeta())
                        folder = model.folderMetaList.last()
                        projection = arrayOf(MediaStore.Images.ImageColumns._ID, model.mediaFolder)
                        cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                projection, model.mediaFolder + " = ?", arrayOf(e), model.sortOrder)
                        while (cursor!!.moveToNext()) {
                            //TODO add from recorded page
                            folder.uriList.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor!!.getLong(cursor!!.getColumnIndex(MediaStore.Images.ImageColumns._ID))))
                        }
                        cursor!!.moveToFirst()
                        folder.title = cursor!!.getString(cursor!!.getColumnIndex(model.mediaFolder))
                    }
                    model.runOCR(mainActivity)
                }
                .show()
    }

    // onDestroy sub method
    private fun setBookTable() {
        for (f in model.folderMetaList) {
            if (f.saverPermit) {
                if (!f.isPageUpdated) f.isPageUpdated = true
                f.titleLastPage = """${f.title}Page: ${f.page}""".trimIndent()
                myDBHelper!!.open()
                if (model.threadIndex > 0 && myDBHelper!!.isNewTitle(f.title)) {
                    if (myDBHelper!!.insertColumn(f.title, f.page.toLong(), f.titleLastPage, 0) != -1L)
                        Log.i("setBookTable", "DB에 삽입됨 : " + f.title + "  " + f.page)
                    else Log.i("setBookTable", "DB에 삽입 에러 -1 : " + f.title + "  " + f.page)
                } else if (model.threadIndex > 0 && !myDBHelper!!.isNewTitle(f.title)) {
                    if (myDBHelper!!.updateColumn(myDBHelper!!.getIdByTitle(f.title), f.title, f.page.toLong(), f.titleLastPage, 0))
                        Log.i("setBookTable", "DB 갱신 됨 : " + f.title + "  " + f.page)
                    else Log.i("setBookTable", "DB 갱신 실패 updateColumn <= 0 : " + f.title + "  " + f.page)
                }
            }
        }
    }

    // onCreate sub method
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

    // service terminate
    fun terminateService() {
        Log.i("Service", "terminateService called is bound? ${model.mIsBound}")
        if (model.mIsBound) {
            val msg = Message.obtain(null, TransService.DISCONNECT)

            Log.i("DB", "now service will terminate")
            try {
                mServiceMessenger.send(msg)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
            unbindService(mConnection)
            model.mIsBound = false
        }
    }

    // Activity life cycle
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = mTts.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED)
                Log.e(model.TAG, "TextToSpeech 초기화 에러!")
            else views.mPlayButton.isEnabled = true
        } else Log.e(model.TAG, "TextToSpeech 초기화 에러!")
    }

    public override fun onDestroy() {
        terminateService()
        mTts.stop()
        mTts.shutdown()
        Log.i("onDestroy", "onDestroy() threadIndex: ${model.threadIndex}")
        if (model.threadIndex > 0 && model.safUri != null) {
            model.ocrResult = views.mEditOcrResult.text.toString()
            model.frw.alterDocument(mainActivity, model.ocrResult, model.safUri)
            setBookTable()
        } else Log.i("onPause()", "thread ${model.threadIndex > 0} safUri ${model.safUri != null}")
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