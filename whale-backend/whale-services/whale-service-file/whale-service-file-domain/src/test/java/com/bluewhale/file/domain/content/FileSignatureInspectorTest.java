package com.bluewhale.file.domain.content;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link FileSignatureInspector} 文件头魔数识别单元测试。
 *
 * @author BlueWhale
 */
class FileSignatureInspectorTest {

    /**
     * 按无符号字节值构造字节数组
     *
     * @param values 无符号字节值（0~255）
     * @return 字节数组
     */
    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    @Test
    @DisplayName("文档与压缩容器：PDF/ZIP/PNG/JPEG/GIF/GZIP/RAR/7Z/OLE2 均可识别")
    void shouldDetectDocumentTypes() {
        assertEquals(FileMagicType.PDF, FileSignatureInspector.detect(
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(FileMagicType.ZIP, FileSignatureInspector.detect(bytes(0x50, 0x4B, 0x03, 0x04)));
        assertEquals(FileMagicType.ZIP, FileSignatureInspector.detect(bytes(0x50, 0x4B, 0x05, 0x06)));
        assertEquals(FileMagicType.ZIP, FileSignatureInspector.detect(bytes(0x50, 0x4B, 0x07, 0x08)));
        assertEquals(FileMagicType.PNG, FileSignatureInspector.detect(
                bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)));
        assertEquals(FileMagicType.JPEG, FileSignatureInspector.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0)));
        assertEquals(FileMagicType.GIF, FileSignatureInspector.detect(
                "GIF87a".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(FileMagicType.GIF, FileSignatureInspector.detect(
                "GIF89a".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(FileMagicType.GZIP, FileSignatureInspector.detect(bytes(0x1F, 0x8B, 0x08)));
        assertEquals(FileMagicType.RAR, FileSignatureInspector.detect(
                bytes(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)));
        assertEquals(FileMagicType.SEVEN_ZIP, FileSignatureInspector.detect(
                bytes(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)));
        assertEquals(FileMagicType.OLE2, FileSignatureInspector.detect(
                bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)));
    }

    @Test
    @DisplayName("音视频：MP4 从偏移 4 识别 ftyp，MP3 支持 ID3 与帧同步")
    void shouldDetectMediaTypes() {
        assertEquals(FileMagicType.MP4, FileSignatureInspector.detect(
                "0000ftypisom".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(FileMagicType.MP3, FileSignatureInspector.detect(
                "ID3\u0004\u0000".getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals(FileMagicType.MP3, FileSignatureInspector.detect(bytes(0xFF, 0xFB, 0x90, 0x00)));
    }

    @Test
    @DisplayName("可执行文件：EXE/ELF/Mach-O 各变体均可识别")
    void shouldDetectExecutables() {
        assertEquals(FileMagicType.EXE, FileSignatureInspector.detect("MZ\u0090".getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals(FileMagicType.ELF, FileSignatureInspector.detect(bytes(0x7F, 0x45, 0x4C, 0x46)));
        assertEquals(FileMagicType.MACHO, FileSignatureInspector.detect(bytes(0xFE, 0xED, 0xFA, 0xCE)));
        assertEquals(FileMagicType.MACHO, FileSignatureInspector.detect(bytes(0xFE, 0xED, 0xFA, 0xCF)));
        assertEquals(FileMagicType.MACHO, FileSignatureInspector.detect(bytes(0xCE, 0xFA, 0xED, 0xFE)));
        assertEquals(FileMagicType.MACHO, FileSignatureInspector.detect(bytes(0xCF, 0xFA, 0xED, 0xFE)));
        assertEquals(FileMagicType.MACHO, FileSignatureInspector.detect(bytes(0xCA, 0xFE, 0xBA, 0xBE)));
    }

    @Test
    @DisplayName("短头部与空头部：按无法识别处理且不越界")
    void shouldReturnUnknownForShortHeader() {
        assertEquals(FileMagicType.UNKNOWN, FileSignatureInspector.detect(null));
        assertEquals(FileMagicType.UNKNOWN, FileSignatureInspector.detect(new byte[0]));
        assertEquals(FileMagicType.UNKNOWN, FileSignatureInspector.detect(bytes(0x50)));
        assertEquals(FileMagicType.UNKNOWN, FileSignatureInspector.detect("%PD".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    @DisplayName("ftyp 不在偏移 4 时不识别为 MP4")
    void shouldRequireFtypAtOffsetFour() {
        assertEquals(FileMagicType.UNKNOWN, FileSignatureInspector.detect(
                "ftypisom".getBytes(StandardCharsets.US_ASCII)));
    }
}
