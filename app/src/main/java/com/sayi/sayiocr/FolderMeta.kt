package com.sayi.sayiocr

import android.net.Uri

class FolderMeta {
    var title = "no title"
    var page = 0
    var titleLastPage = "$title  Page: $page"
    var isPageUpdated = false
    var uriList = ArrayList<Uri>()
    var saverPermit = false
}