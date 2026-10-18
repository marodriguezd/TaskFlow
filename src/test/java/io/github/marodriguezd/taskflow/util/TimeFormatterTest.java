package io.github.marodriguezd.taskflow.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeFormatterTest {

    @ParameterizedTest
    @CsvSource({
        "1500, '25:00'",
        "0, '00:00'",
        "-10, '00:00'",
        "-1, '00:00'",
        "-2147483648, '00:00'",
        "5, '00:05'",
        "9, '00:09'",
        "10, '00:10'",
        "59, '00:59'",
        "60, '01:00'",
        "65, '01:05'",
        "599, '09:59'",
        "600, '10:00'",
        "3599, '59:59'",
        "3600, '60:00'",
        "6000, '100:00'",
        "7205, '120:05'",
        "2147483647, '35791394:07'"
    })
    @DisplayName("Formats seconds to MM:SS correctly")
    void testFormat(int seconds, String expected) {
        assertThat(TimeFormatter.format(seconds)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Parses minutes to seconds correctly")
    void testParseMinutesToSeconds() {
        assertThat(TimeFormatter.parseMinutesToSeconds(25)).isEqualTo(1500);
        assertThat(TimeFormatter.parseMinutesToSeconds(1)).isEqualTo(60);
        assertThat(TimeFormatter.parseMinutesToSeconds(0)).isEqualTo(60);
    }

    @Test
    @DisplayName("TimeFormatter does not reference String.format in bytecode")
    void testNoStringFormatInBytecode() throws Exception {
        try (InputStream in = TimeFormatter.class.getResourceAsStream("TimeFormatter.class")) {
            assertThat(in).isNotNull();
            byte[] bytes = in.readAllBytes();
            assertThat(hasStringFormatMethodRef(bytes)).isFalse();
            String classContent = new String(bytes, StandardCharsets.ISO_8859_1);
            assertThat(classContent)
                    .doesNotContain("(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;");
            assertThat(classContent).doesNotContain("java/util/Locale");
        }
    }

    private boolean hasStringFormatMethodRef(byte[] bytes) throws Exception {
        java.io.DataInputStream dis =
                new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes));
        int magic = dis.readInt();
        if (magic != 0xCAFEBABE) {
            throw new IllegalArgumentException(
                    "Invalid class file magic: " + Integer.toHexString(magic));
        }
        dis.readUnsignedShort(); // minor
        dis.readUnsignedShort(); // major
        int count = dis.readUnsignedShort();

        java.util.Map<Integer, String> utf8s = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> classes = new java.util.HashMap<>();
        java.util.Map<Integer, int[]> nameAndTypes = new java.util.HashMap<>();
        java.util.List<int[]> methodRefs = new java.util.ArrayList<>();

        for (int i = 1; i < count; i++) {
            int tag = dis.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8s.put(i, dis.readUTF());
                case 3, 4 -> dis.skipBytes(4);
                case 5, 6 -> {
                    dis.skipBytes(8);
                    i++;
                }
                case 7 -> classes.put(i, dis.readUnsignedShort());
                case 8, 16, 19, 20 -> dis.skipBytes(2);
                case 9, 11 -> dis.skipBytes(4);
                case 10 ->
                        methodRefs.add(
                                new int[] {dis.readUnsignedShort(), dis.readUnsignedShort()});
                case 12 ->
                        nameAndTypes.put(
                                i, new int[] {dis.readUnsignedShort(), dis.readUnsignedShort()});
                case 15 -> dis.skipBytes(3);
                case 17, 18 -> dis.skipBytes(4);
                default -> throw new IllegalStateException("Unknown constant pool tag: " + tag);
            }
        }

        for (int[] ref : methodRefs) {
            int classIdx = ref[0];
            int natIdx = ref[1];
            String className = utf8s.get(classes.get(classIdx));
            int[] nat = nameAndTypes.get(natIdx);
            String methodName = (nat != null) ? utf8s.get(nat[0]) : null;
            if ("java/lang/String".equals(className) && "format".equals(methodName)) {
                return true;
            }
        }
        return false;
    }
}
