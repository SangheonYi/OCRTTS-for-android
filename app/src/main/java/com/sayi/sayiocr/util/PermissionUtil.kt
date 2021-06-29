package com.sayi.sayiocr.util

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

object PermissionUtil {
    const val REQUEST_STORAGE = 1
    private val PERMISSIONS_STORAGE = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    fun checkPermissions(activity: Activity?, permission: String?): Boolean {
        val permissionResult = ActivityCompat.checkSelfPermission(activity!!, permission!!)
        return permissionResult == PackageManager.PERMISSION_GRANTED
    }

    fun requestExternalPermissions(activity: Activity?) {
        ActivityCompat.requestPermissions(activity!!, PERMISSIONS_STORAGE, REQUEST_STORAGE)
    }

    fun verifyPermission(grantresults: IntArray): Boolean {
        if (grantresults.size < 1) {
            return false
        }
        for (result in grantresults) {
            if (result != PackageManager.PERMISSION_GRANTED) return false
        }
        return true
    }
}