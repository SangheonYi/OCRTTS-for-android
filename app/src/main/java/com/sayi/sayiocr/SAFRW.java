package com.sayi.sayiocr;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class SAFRW {
    private Intent intent;
    private String fName;

    public SAFRW(){
        fName = null;
    }

    public Intent getIntent(){
        return intent;
    }
    public String getfName(){
        return fName;
    }
    public void setfName(String fileName){
        fName = fileName;
    }
    public Intent performFileSearch(String mimeType){
        intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        return intent;
    }

    public Intent createFile(String mimeType, String fileName){
        intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        return intent;
    }

    public void alterDocument(Activity activity, String fileContent, Uri uri){
        Log.i("SAFRW", "alterDocument str내용 : " + fileContent);
        Log.i("SAFRW", "uri: " + uri);
        try {
            ParcelFileDescriptor parcelFileDescriptor = activity.getContentResolver().
                    openFileDescriptor(uri, "wa");
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(parcelFileDescriptor.getFileDescriptor()));
            bufferedWriter.write(fileContent);
            bufferedWriter.flush();
            bufferedWriter.close();
            parcelFileDescriptor.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i("SAFRW", "try 완수 ");
    }
}
