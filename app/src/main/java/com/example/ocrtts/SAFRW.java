package com.example.ocrtts;

import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;

public class SAFRW {

    public SAFRW(){

    }
    public Intent performFileSearch(String mimeType){
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        return intent;
    }

    private Intent createFile(String mimeType, String fileName){
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        return intent;
    }

    private void alterDocument(String fileContent, ParcelFileDescriptor descriptor){
        Log.i("SAFRW", "alterDocument str내용 : " + fileContent);
        try {
            ParcelFileDescriptor parcelFileDescriptor = descriptor;
            FileWriter fileWriter = new FileWriter(parcelFileDescriptor.getFileDescriptor());
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.append(fileContent);
            bufferedWriter.flush();
            bufferedWriter.close();
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i("SAFRW", "try 완수 ");
    }
}
