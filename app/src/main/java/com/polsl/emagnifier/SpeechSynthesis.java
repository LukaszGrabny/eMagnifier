package com.polsl.emagnifier;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

        public class SpeechSynthesis implements TextToSpeech.OnInitListener {
    private Context context;
    TextToSpeech tts;
    public SpeechSynthesis (Context context) {
        this.context = context;
        tts = new TextToSpeech(context,this);
    }
    static Collection<String> splitStringBySize(String str, int size) {
        ArrayList<String> split = new ArrayList<>();
        for (int i = 0; i <= str.length() / size; i++) {
            split.add(str.substring(i * size, Math.min((i + 1) * size, str.length())));
        }
        return split;
    }

     void speakOut(String text) {
        tts.setLanguage(new Locale("pl", "PL"));
    //    tts.setVoice(new Voice("high",new Locale("pl", "PL"),Voice.QUALITY_VERY_HIGH,Voice.LATENCY_VERY_LOW,false,null));
        tts.speak(text.toLowerCase(new Locale("pl", "PL")), TextToSpeech.QUEUE_FLUSH,null,"");
    }
     void speakOutLong(Collection<String> text) {
        tts.setLanguage(new Locale("pl", "PL"));
        for (String s : text){
            tts.speak(s.toLowerCase(new Locale("pl", "PL")),TextToSpeech.QUEUE_ADD,null,"");
        }
    }

    @Override
    public void onInit(int status) {
        if(status == TextToSpeech.SUCCESS){
        }
    }
}
