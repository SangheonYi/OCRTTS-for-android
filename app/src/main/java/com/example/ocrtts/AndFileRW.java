package com.example.ocrtts;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class AndFileRW {
    private String path;
    private String file_name = "no title.txt";
    private String str;
    private boolean append_bool;

    public AndFileRW(String in_path, String in_str, String in_file_name, boolean in_bool){
        this.path = in_path;
        this.file_name = in_file_name;
        this.str = in_str;
        this.append_bool = in_bool;
    }
    public AndFileRW(String in_path, String in_str, boolean in_bool){
        this.path = in_path;
        this.str = in_str;
        this.append_bool = in_bool;
    }

    public String getPath() {
        return path;
    }

    public String getFile_name() {
        return file_name;
    }

    public String getStr() {
        return str;
    }

    public boolean isAppend_bool() {
        return append_bool;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setFile_name(String file_name) {
        this.file_name = file_name;
    }

    public void setStr(String str) {
        this.str = str;
    }

    public void setAppend_bool(boolean append_bool) {
        this.append_bool = append_bool;
    }

    public String txtToStr() {
        File file = new File(path, file_name);
        Log.i("에헤라", "load파일 경로 : " + path + file_name);
        FileReader fr;
        BufferedReader bufrd;
        String tmp = "";
        StringBuffer strbf = new StringBuffer(tmp);


        if (file.exists()) {
            try {
                // open file.
                fr = new FileReader(file);
                bufrd = new BufferedReader(fr);

                while ((tmp =bufrd.readLine()) != null){
                    strbf.append(tmp);
                }

                bufrd.close();
                fr.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else
            Log.e("에헤라", "load파일 없다 : " + path);

        return strbf.toString();
    }

    public boolean strToTxt() {

        File file = new File(path, file_name);
        //  /storage/emulated/0/Download     /storage/1209-0706/Download        /storage/2C70-B4D9/
        FileWriter fw;
        BufferedWriter bufwr;

        Log.i("에헤라", "save파일 경로 : " + path + file_name);
        Log.i("에헤라", "str내용 : " + str);
        if (!file.exists()) { // 폴더 없을 경우
            Log.i("에헤라", "파일 없음 ");
        }

        try {
            // open file.
            fw = new FileWriter(file, append_bool);
            bufwr = new BufferedWriter(fw);

            bufwr.append(str);

            // write data to the file.
            bufwr.flush();

            // close file.
            bufwr.close();

            fw.close();

            Log.i("에헤라", "try 완수 ");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    public boolean delTxt(){
        File file = new File(path, file_name);

        if (file.delete()){
            Log.i("에헤라", "파일 지움 경로 : " + path + file_name);
            return true;
        }
        else{
            Log.i("에헤라", "파일 못 지움 경로 : " + path);
            return false;
        }
    }
}
