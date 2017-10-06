package com.grp22.arcm;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Andrew on 4/9/17.
 */

public class SpeechCommandProcessor {
    private String command;
    private boolean isRepeatable;
    private int repetition;
    private int targetOrientation;

    public SpeechCommandProcessor() {
        this.command = null;
        this.isRepeatable = false;
        this.repetition = 1;
        this.targetOrientation = 0;
    }

    public void process(String speech) {
        speech = speech.toLowerCase();

        Map<String, String> replacements = new HashMap<String, String>() {{
            put("for word", "forward");
            put("foreword", "forward");
            put("fore word", "forward");
            put("four word", "forward");

            put("one ", "1");
            put("two ", "2");
            put("three ", "3");
            put("four ", "4");
            put("five ", "5");
            put("six ", "6");
            put("seven ", "7");
            put("eight ", "8");
            put("nine ", "9");
            put("ten ", "10");

            put("to ", "2");
            put("tree ", "3");
            put("for ", "4");

            put("wright", "right");
            put("town", "down");
            put("last", "left");
        }};

        String regexp = "for word|foreword|fore word|four word|one |two |three |four |five |six |seven |eight |nine |ten |to |tree |for |wright|town|last";

        StringBuffer sb = new StringBuffer();
        Pattern p = Pattern.compile(regexp);
        Matcher m = p.matcher(speech);

        while (m.find())
            m.appendReplacement(sb, replacements.get(m.group()));
        m.appendTail(sb);

        speech = sb.toString();

        Log.d("Command: ", speech);
        if (speech.contains("forward")) {
            command = "forward";
            isRepeatable = true;
            targetOrientation = 0;
        } else if (speech.contains("back")) {
            command = "reverse";
            isRepeatable = true;
            targetOrientation = 2;
        } else if (speech.contains("turn left")) {
            command = "rotateLeft";
            isRepeatable = true;
            targetOrientation = 2;
        } else if (speech.contains("turn right")) {
            command = "rotateRight";
            isRepeatable = true;
            targetOrientation = 0;
        } else if (speech.contains("left")) {
            command = "left";
            isRepeatable = true;
            targetOrientation = 3;
        } else if (speech.contains("right")) {
            command = "right";
            isRepeatable = true;
            targetOrientation = 1;
        } else if (speech.contains("exploration")) {
            command = "pEX_START";
            isRepeatable = false;
        } else if (speech.contains("fastest")) {
            command = "pFP_START";
            isRepeatable = false;
        } else
            command = null;

        Matcher matcher = Pattern.compile("\\d+").matcher(speech);
        if (matcher.find())
            repetition = Integer.valueOf(matcher.group());
        else
            repetition = 1;
    }

    public String getCommand() {
        return this.command;
    }

    public int getRepetition() {
        return this.repetition;
    }

    public boolean isRepeatable() {
        return this.isRepeatable;
    }

    public int getTargetOrientation() {
        return this.targetOrientation;
    }
}
