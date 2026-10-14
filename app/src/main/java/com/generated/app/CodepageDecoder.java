package com.generated.app;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class CodepageDecoder {

    private static String currentCodepage = "CP862";

    public static void setCodepage(String cp) {
        if (cp != null) {
            currentCodepage = cp;
        }
    }

    public static String getCodepage() {
        return currentCodepage;
    }

    public static String decode83(byte[] buffer, int offset, int length) {
        try {
            Charset charset;
            if ("CP862".equalsIgnoreCase(currentCodepage)) {
                charset = Charset.forName("Cp862");
            } else if ("Windows-1255".equalsIgnoreCase(currentCodepage)) {
                charset = Charset.forName("windows-1255");
            } else {
                charset = Charset.forName("Cp437");
            }

            byte[] workBuf = new byte[length];
            System.arraycopy(buffer, offset, workBuf, 0, length);
            if (workBuf[0] == 0x05) {
                workBuf[0] = (byte) 0xE5;
            }

            String decoded = new String(workBuf, charset).trim();
            return decoded;
        } catch (Exception e) {
            return new String(buffer, offset, length, StandardCharsets.ISO_8859_1).trim();
        }
    }

    public static String formatShortName(String rawNameStr) {
        if (rawNameStr == null) return "";
        rawNameStr = rawNameStr.trim();
        if (rawNameStr.isEmpty()) return "";
        
        if (rawNameStr.length() <= 8) {
            return rawNameStr;
        }
        
        // If extension is embedded without dot, separate name and extension
        String namePart = rawNameStr.substring(0, Math.min(8, rawNameStr.length())).trim();
        String extPart = "";
        if (rawNameStr.length() > 8) {
            extPart = rawNameStr.substring(8).trim();
        }
        
        if (extPart.isEmpty()) {
            return namePart;
        } else {
            return namePart + "." + extPart;
        }
    }
}
