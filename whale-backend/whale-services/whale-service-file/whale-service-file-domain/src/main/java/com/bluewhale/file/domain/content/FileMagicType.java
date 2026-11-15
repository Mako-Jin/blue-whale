package com.bluewhale.file.domain.content;

/**
 * 文件头魔数类型：按内容识别出的真实文件类型。
 *
 * <p>同一容器可能有多个来源扩展名：{@code docx}/{@code xlsx}/{@code pptx} 识别为 {@link #ZIP}，
 * {@code doc}/{@code xls}/{@code ppt} 识别为 {@link #OLE2}，扩展名与内容类型的对应关系由应用层维护。
 * 无法识别的头部统一返回 {@link #UNKNOWN}，调用方应放行以避免误伤自定义与加密格式。</p>
 *
 * @author BlueWhale
 */
public enum FileMagicType {

    /** PDF 文档：头部 {@code %PDF-} */
    PDF("PDF 文档"),

    /** ZIP 容器（含 docx/xlsx/pptx）：头部 {@code PK} */
    ZIP("ZIP 容器"),

    /** PNG 图片：8 字节 PNG 签名 */
    PNG("PNG 图片"),

    /** JPEG 图片：头部 {@code FF D8 FF} */
    JPEG("JPEG 图片"),

    /** GIF 图片：头部 {@code GIF87a} / {@code GIF89a} */
    GIF("GIF 图片"),

    /** GZIP 压缩流：头部 {@code 1F 8B} */
    GZIP("GZIP 压缩流"),

    /** RAR 压缩包：头部 {@code Rar!} */
    RAR("RAR 压缩包"),

    /** 7-Zip 压缩包：头部 {@code 37 7A BC AF 27 1C} */
    SEVEN_ZIP("7-Zip 压缩包"),

    /** OLE2 复合文档（含 doc/xls/ppt）：8 字节复合文档签名 */
    OLE2("OLE2 复合文档"),

    /** MP4 视频：偏移 4 起 {@code ftyp} */
    MP4("MP4 视频"),

    /** MP3 音频：头部 {@code ID3} 或 MPEG 帧同步 */
    MP3("MP3 音频"),

    /** Windows 可执行文件：头部 {@code MZ} */
    EXE("Windows 可执行文件"),

    /** Linux 可执行文件：头部 {@code 7F 45 4C 46} */
    ELF("Linux 可执行文件"),

    /** macOS Mach-O 可执行文件：32/64 位与通用二进制魔数 */
    MACHO("macOS 可执行文件"),

    /** 未识别的头部 */
    UNKNOWN("未知类型");

    /** 类型描述 */
    private final String description;

    /**
     * 以描述构造内容类型
     *
     * @param description 类型描述
     */
    FileMagicType(String description) {
        this.description = description;
    }

    /**
     * 获取类型描述
     *
     * @return 类型描述
     */
    public String getDescription() {
        return description;
    }
}
