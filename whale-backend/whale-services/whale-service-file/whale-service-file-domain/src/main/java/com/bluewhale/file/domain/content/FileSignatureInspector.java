package com.bluewhale.file.domain.content;

/**
 * 文件头魔数识别器：按固定偏移匹配已知魔数，输出真实内容类型。
 *
 * <p>纯静态、无状态、无第三方依赖；头部不足判定长度时按不匹配处理，不会越界读取。
 * 调用方只需提供文件前 32 字节即可覆盖全部类型（MP4 的 {@code ftyp} 位于偏移 4）。</p>
 *
 * @author BlueWhale
 */
public final class FileSignatureInspector {

    /** 魔数嗅探所需的头部字节数 */
    public static final int HEADER_LENGTH = 32;

    /** MP4 box 类型标识的偏移量 */
    private static final int MP4_BOX_OFFSET = 4;

    /** MPEG 音频帧同步位掩码（高 3 位全 1） */
    private static final int MPEG_SYNC_MASK = 0xE0;

    /**
     * 私有构造：工具类不允许实例化
     */
    private FileSignatureInspector() {
    }

    /**
     * 识别文件头魔数对应的内容类型
     *
     * @param header 文件前若干字节，可为 {@code null} 或短于 {@link #HEADER_LENGTH}
     * @return 识别出的内容类型；无法识别时返回 {@link FileMagicType#UNKNOWN}
     */
    public static FileMagicType detect(byte[] header) {
        if (header == null || header.length == 0) {
            return FileMagicType.UNKNOWN;
        }
        FileMagicType document = detectDocument(header);
        if (document != FileMagicType.UNKNOWN) {
            return document;
        }
        FileMagicType media = detectMedia(header);
        if (media != FileMagicType.UNKNOWN) {
            return media;
        }
        return detectExecutable(header);
    }

    /**
     * 识别文档与压缩容器类型
     *
     * @param header 文件头字节，非空
     * @return 内容类型；未命中时返回 {@link FileMagicType#UNKNOWN}
     */
    private static FileMagicType detectDocument(byte[] header) {
        if (matches(header, 0, '%', 'P', 'D', 'F', '-')) {
            return FileMagicType.PDF;
        }
        if (matches(header, 0, 'G', 'I', 'F', '8', '7', 'a')
                || matches(header, 0, 'G', 'I', 'F', '8', '9', 'a')) {
            return FileMagicType.GIF;
        }
        if (matches(header, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return FileMagicType.PNG;
        }
        if (matches(header, 0, 0xFF, 0xD8, 0xFF)) {
            return FileMagicType.JPEG;
        }
        if (matches(header, 0, 0x1F, 0x8B)) {
            return FileMagicType.GZIP;
        }
        if (isZip(header)) {
            return FileMagicType.ZIP;
        }
        if (matches(header, 0, 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07)) {
            return FileMagicType.RAR;
        }
        if (matches(header, 0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)) {
            return FileMagicType.SEVEN_ZIP;
        }
        if (matches(header, 0, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
            return FileMagicType.OLE2;
        }
        return FileMagicType.UNKNOWN;
    }

    /**
     * 识别音视频类型
     *
     * @param header 文件头字节，非空
     * @return 内容类型；未命中时返回 {@link FileMagicType#UNKNOWN}
     */
    private static FileMagicType detectMedia(byte[] header) {
        if (matches(header, MP4_BOX_OFFSET, 'f', 't', 'y', 'p')) {
            return FileMagicType.MP4;
        }
        if (isMp3(header)) {
            return FileMagicType.MP3;
        }
        return FileMagicType.UNKNOWN;
    }

    /**
     * 识别可执行文件类型
     *
     * @param header 文件头字节，非空
     * @return 内容类型；未命中时返回 {@link FileMagicType#UNKNOWN}
     */
    private static FileMagicType detectExecutable(byte[] header) {
        if (matches(header, 0, 'M', 'Z')) {
            return FileMagicType.EXE;
        }
        if (matches(header, 0, 0x7F, 0x45, 0x4C, 0x46)) {
            return FileMagicType.ELF;
        }
        if (isMachO(header)) {
            return FileMagicType.MACHO;
        }
        return FileMagicType.UNKNOWN;
    }

    /**
     * 判定是否为 ZIP 容器（含普通包、空包与分卷包）
     *
     * @param header 文件头字节，非空
     * @return 命中任一 ZIP 签名时返回 {@code true}
     */
    private static boolean isZip(byte[] header) {
        return matches(header, 0, 0x50, 0x4B, 0x03, 0x04)
                || matches(header, 0, 0x50, 0x4B, 0x05, 0x06)
                || matches(header, 0, 0x50, 0x4B, 0x07, 0x08);
    }

    /**
     * 判定是否为 MP3：优先 {@code ID3} 标签头，其次 MPEG 帧同步位
     *
     * @param header 文件头字节，非空
     * @return 命中 MP3 特征时返回 {@code true}
     */
    private static boolean isMp3(byte[] header) {
        if (matches(header, 0, 'I', 'D', '3')) {
            return true;
        }
        return header.length >= 2 && (header[0] & 0xFF) == 0xFF && (header[1] & MPEG_SYNC_MASK) == MPEG_SYNC_MASK;
    }

    /**
     * 判定是否为 Mach-O：32/64 位双端序与通用二进制魔数
     *
     * @param header 文件头字节，非空
     * @return 命中任一 Mach-O 魔数时返回 {@code true}
     */
    private static boolean isMachO(byte[] header) {
        return matches(header, 0, 0xFE, 0xED, 0xFA, 0xCE)
                || matches(header, 0, 0xFE, 0xED, 0xFA, 0xCF)
                || matches(header, 0, 0xCE, 0xFA, 0xED, 0xFE)
                || matches(header, 0, 0xCF, 0xFA, 0xED, 0xFE)
                || matches(header, 0, 0xCA, 0xFE, 0xBA, 0xBE);
    }

    /**
     * 判定头部在指定偏移处是否匹配期望字节序列
     *
     * @param header 文件头字节
     * @param offset 起始偏移
     * @param expected 期望字节（按无符号整数给出）
     * @return 完全匹配且未越界时返回 {@code true}
     */
    private static boolean matches(byte[] header, int offset, int... expected) {
        if (offset < 0 || header.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
