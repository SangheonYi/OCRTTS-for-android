package com.example.ocrtts

import android.net.Uri

class FolderMeta {
    var title = "no title"
    var page = 0
    var titleLastPage = "$title\nPage: $page"
    var pickedNumber = 0
    var isPageUpdated = false
    var folderTotalPages = 0
    var uriList = ArrayList<Uri>()
}