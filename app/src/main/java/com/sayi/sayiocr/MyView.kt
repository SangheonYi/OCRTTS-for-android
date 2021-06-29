// initiate views class

package com.sayi.sayiocr

import android.graphics.Color
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.content.res.ResourcesCompat
import com.example.sayiocr.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import com.sayi.sayiocr.ui.MainActivity

class MyView {
    lateinit var writeMADB: MaterialAlertDialogBuilder
    lateinit var albumMADB: MaterialAlertDialogBuilder
    lateinit var folderMADB: MaterialAlertDialogBuilder
    lateinit var saveHelp: MaterialAlertDialogBuilder

    fun viewsCreate(main: MainActivity){
        val resources = main.resources
        val theme = main.theme

        // Ad
        main.bindings.adView.loadAd(AdRequest.Builder().build())

        // Dialog
        writeMADB = MaterialAlertDialogBuilder(main)
        albumMADB = MaterialAlertDialogBuilder(main)
        folderMADB = MaterialAlertDialogBuilder(main)
        saveHelp =MaterialAlertDialogBuilder(main)

        main.bindings.play.setOnClickListener(main)
        main.bindings.stop.setOnClickListener(main)
        main.bindings.slower.setOnClickListener(main)
        main.bindings.faster.setOnClickListener(main)
        main.bindings.before.setOnClickListener(main)
        main.bindings.next.setOnClickListener(main)
        main.bindings.album.setOnClickListener(main)
        main.bindings.speedDial.addActionItem(
                SpeedDialActionItem.Builder(R.id.fab_write_txt, R.drawable.content_save_outline)
                        .setLabel(main.getString(R.string.label_fab_create_txt))
                        .setLabelColor(Color.WHITE)
                        .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.material_blue_500, theme))
                        .setLabelClickable(false)
                        .setTheme(R.style.AppTheme)
                        .create()
        )
        main.bindings.speedDial.addActionItem(
                SpeedDialActionItem.Builder(R.id.fab_DB, R.drawable.delete_outline)
                        .setLabel(main.getString(R.string.label_fab_DB))
                        .setLabelColor(Color.WHITE)
                        .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.material_blue_500, theme))
                        .setLabelClickable(false)
                        .setTheme(R.style.AppTheme)
                        .create()
        )
        main.bindings.speedDial.addActionItem(
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