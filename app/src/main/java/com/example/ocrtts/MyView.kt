package com.example.ocrtts

import android.graphics.Color
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView

class MyView {
    lateinit var mEditOcrResult: EditText //변환된 Text View
    lateinit var mEditReadingState: EditText //읽는 상태 View
    lateinit var mEditOCRProgress: EditText //OCR 진행 상태
    lateinit var albumButton: ImageButton
    lateinit var mPlayButton: ImageButton
    lateinit var mStopButton: ImageButton
    lateinit var mBeforeButton: ImageButton
    lateinit var mNextButton: ImageButton
    lateinit var mFasterButton: ImageButton
    lateinit var mSlowerButton: ImageButton
    lateinit var speedDialView: SpeedDialView
    lateinit var writeMADB: MaterialAlertDialogBuilder
    lateinit var albumMADB: MaterialAlertDialogBuilder
    lateinit var folderMADB: MaterialAlertDialogBuilder

    fun viewsCreate(main: MainActivity){
        val resources = main.resources
        val theme = main.theme

        // TextViews
        mEditOcrResult = main.findViewById(R.id.edit_ocrresult)
        mEditReadingState = main.findViewById(R.id.ReadingState_bar)
        mEditOCRProgress = main.findViewById(R.id.OCRprogress_bar)
        // Button
        mPlayButton = main.findViewById(R.id.play)
        mStopButton = main.findViewById(R.id.stop)
        mBeforeButton = main.findViewById(R.id.before)
        mNextButton = main.findViewById(R.id.next)
        mFasterButton = main.findViewById(R.id.faster)
        mSlowerButton = main.findViewById(R.id.slower)
        albumButton = main.findViewById(R.id.btn_album)
        speedDialView = main.findViewById(R.id.speedDial)

        // Dialog
        writeMADB = MaterialAlertDialogBuilder(main)
        albumMADB = MaterialAlertDialogBuilder(main)
        folderMADB = MaterialAlertDialogBuilder(main)

        mPlayButton.setOnClickListener(main)
        mStopButton.setOnClickListener(main)
        mSlowerButton.setOnClickListener(main)
        mFasterButton.setOnClickListener(main)
        mBeforeButton.setOnClickListener(main)
        mNextButton.setOnClickListener(main)
        albumButton.setOnClickListener(main)
        speedDialView.addActionItem(
                SpeedDialActionItem.Builder(R.id.fab_write_txt, R.drawable.content_save_outline)
                        .setLabel(main.getString(R.string.label_fab_create_txt))
                        .setLabelColor(Color.WHITE)
                        .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.material_blue_500, theme))
                        .setLabelClickable(false)
                        .setTheme(R.style.AppTheme)
                        .create()
        )
        speedDialView.addActionItem(
                SpeedDialActionItem.Builder(R.id.fab_DB, R.drawable.delete_outline)
                        .setLabel(main.getString(R.string.label_fab_DB))
                        .setLabelColor(Color.WHITE)
                        .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.material_blue_500, theme))
                        .setLabelClickable(false)
                        .setTheme(R.style.AppTheme)
                        .create()
        )
        speedDialView.addActionItem(
                SpeedDialActionItem.Builder(R.id.fab_flush_edittxt, R.drawable.refresh)
                        .setLabel(main.getString(R.string.label_fab_flush_edittxt))
                        .setLabelColor(Color.WHITE)
                        .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.material_blue_500, theme))
                        .setLabelClickable(false)
                        .setTheme(R.style.AppTheme)
                        .create()
        )
    }
}