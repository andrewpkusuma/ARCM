package com.grp22.arcm;

/**
 * Created by Andrew on 29/9/17.
 */

public class MapDecoder {
    public static String decode(String code, boolean isPartOne) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            c = Character.toUpperCase(c);
            switch (c) {
                case '0':
                    stringBuilder.append("0000");
                    break;
                case '1':
                    stringBuilder.append("0001");
                    break;
                case '2':
                    stringBuilder.append("0010");
                    break;
                case '3':
                    stringBuilder.append("0011");
                    break;
                case '4':
                    stringBuilder.append("0100");
                    break;
                case '5':
                    stringBuilder.append("0101");
                    break;
                case '6':
                    stringBuilder.append("0110");
                    break;
                case '7':
                    stringBuilder.append("0111");
                    break;
                case '8':
                    stringBuilder.append("1000");
                    break;
                case '9':
                    stringBuilder.append("1001");
                    break;
                case 'A':
                    stringBuilder.append("1010");
                    break;
                case 'B':
                    stringBuilder.append("1011");
                    break;
                case 'C':
                    stringBuilder.append("1100");
                    break;
                case 'D':
                    stringBuilder.append("1101");
                    break;
                case 'E':
                    stringBuilder.append("1110");
                    break;
                case 'F':
                    stringBuilder.append("1111");
                    break;
            }
        }
        String result = stringBuilder.toString();
        if (isPartOne) {
            result = result.substring(0, result.length()- 2).substring(2);
        }
        return result;
    }
}
