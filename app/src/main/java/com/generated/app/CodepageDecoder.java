package com.generated.app;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class CodepageDecoder {

    // Rule 5: Default codepage CP862 (Hebrew DOS)
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

            // Handle 0x05 as 0xE5 (first character Japanese/Kanji fix in FAT)
            byte[] workBuf = new byte[length];
            System.arraycopy(buffer, offset, workBuf, 0, length);
            if (workBuf[0] == 0x05) {
                workBuf[0] = (byte) 0xE5;
            }

            // Rule 5: Handle lowercase flags in byte 12 (offset 12 relative to entry start)
            // If buffer has length >= 13, byte at entryOffset + 12 contains NT file name case flags.
            // But here buffer is passed as directory entry segment or name slice.
            String decoded = new String(workBuf, charset).trim();
            
            // Remove trailing spaces in 8.3 name parts
            return decoded;
        } catch (Exception e) {
            return new String(buffer, offset, length, StandardCharsets.ISO_8859_1).trim();
        }
    }
}
