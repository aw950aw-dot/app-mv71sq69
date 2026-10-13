package com.generated.app;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class CodepageDecoder {

    private static String currentCodepage = "CP437";

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
            return new String(buffer, offset, length, charset).trim();
        } catch (Exception e) {
            return new String(buffer, offset, length, StandardCharsets.ISO_8859_1).trim();
        }
    }
}
