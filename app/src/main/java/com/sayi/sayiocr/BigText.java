package com.sayi.sayiocr;

import java.util.ArrayList;

public class BigText {
    private ArrayList<String> Sentence = new ArrayList<>();

    public  BigText(){
        Sentence.add("");
    }

    public ArrayList<String> getSentence() {
        return Sentence;
    }

    public void setSentence(String text) {
        Sentence.clear();
        //String[] tempString =  text.split("(\\.)|(\\?)|(\\\"(\\r|\\n))|(\\!)");
        String[] tempString =  text.split("(\\\")|(\\')|(\\.)|(\\!)|(\\})|(\\])|(\\>)|(\\))|(\\?)");
        for (int i = 0; i< tempString.length; i++){
            Sentence.add(tempString[i].replaceAll(System.getProperty("line.separator"), " ") + " ") ;
        }
    }

    public int getSize(){
        return getSentence().size();
    }

    public String getSentence(int i) {
        return Sentence.get(i);
    }

    public void addSentence(String text) {
        //String[] tempString =  text.split("(\\.)|(\\?)|(\\\"(\\r|\\n))|(\\!)");
        String[] tempString =  text.split("(\\\")|(\\')|(\\.)|(\\!)|(\\})|(\\])|(\\>)|(\\))|(\\?)");
        for (int i = 0; i< tempString.length; i++){
            Sentence.add(tempString[i].replaceAll(System.getProperty("line.separator"), " ") + " ") ;
        }
    }

    public boolean isSentenceNull(int i){
        if (Sentence.get(i).matches("\\s+"))
            return true;
        return false;
    }


}
