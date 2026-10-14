package com.generated.app;

public class FileTypeTest {
    public static void runUnitTests() {
        // Rule 7: Unit test for file type lookup returning UNKNOWN (never null)
        ByteSource dummySource = new ByteSource() {
            @Override
            public long getSize() { return 1024; }
            @Override
            public int readAt(long offset, byte[] buf, int len) { return 0; }
            @Override
            public void close() {}
        };

        FileType t1 = FileTypeDetector.detectTypeByNameOrHeader(dummySource, "", 0, 0);
        assert t1 == FileType.UNKNOWN : "Test failed for empty filename";

        FileType t2 = FileTypeDetector.detectTypeByNameOrHeader(dummySource, "noext", 0, 0);
        assert t2 == FileType.UNKNOWN : "Test failed for noext";

        FileType t3 = FileTypeDetector.detectTypeByNameOrHeader(dummySource, "file.xyz", 0, 0);
        assert t3 == FileType.UNKNOWN : "Test failed for .xyz";

        FileType t4 = FileTypeDetector.detectTypeByNameOrHeader(dummySource, "A.JPG", 0, 0);
        assert t4 == FileType.UNKNOWN : "Test failed for uppercase extension A.JPG";

        FileType t5 = FileTypeDetector.detectTypeByNameOrHeader(dummySource, "a.Jpg", 0, 0);
        assert t5 == FileType.UNKNOWN : "Test failed for mixed case extension a.Jpg";

        FileType t6 = FileTypeDetector.detectTypeByNameOrHeader(dummySource, "image.jpg", 0, 0);
        assert t6 == FileType.JPEG : "Test failed for valid lowercase image.jpg";
    }
}
