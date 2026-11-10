package com.bluewhale.video.domain.service;

import com.bluewhale.video.domain.exception.VideoErrorCode;
import com.bluewhale.video.domain.model.FileAsset;
import com.bluewhale.video.domain.model.UploadRule;

import java.util.Locale;
import java.util.Set;

/**
 * 视频上传校验策略（大小、MIME、magic bytes）。
 *
 * <p>只做纯规则判断，不依赖任何框架与外部 IO：文件元数据由应用层经
 * {@code FileAssetPort} 读取，文件头字节由 {@code ContentStreamRepository}
 * 读取后传入。校验顺序：文件可用 → MIME 白名单 → 大小上限 →
 * magic bytes 与 MIME 一致性。</p>
 *
 * @author BlueWhale
 */
public final class VideoUploadPolicy {

    /** MP4 文件的 {@code ftyp} 标识所在偏移 */
    private static final int MP4_FTYP_OFFSET = 4;

    /** MP4 文件头最小长度（需要读到 {@code ftyp} 四个字节） */
    private static final int MP4_HEAD_MIN_LENGTH = 8;

    /** Matroska / WebM 文件头长度 */
    private static final int WEBM_HEAD_LENGTH = 4;

    /** Matroska / WebM 文件头首字节 */
    private static final int WEBM_MAGIC_1 = 0x1A;

    /** Matroska / WebM 文件头次字节 */
    private static final int WEBM_MAGIC_2 = 0x45;

    /** Matroska / WebM 文件头第三字节 */
    private static final int WEBM_MAGIC_3 = 0xDF;

    /** Matroska / WebM 文件头第四字节 */
    private static final int WEBM_MAGIC_4 = 0xA3;

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private VideoUploadPolicy() {
    }

    /**
     * 校验视频文件是否符合指定内容类型的上传规则。
     *
     * @param asset 文件元数据
     * @param head 文件头字节（可为空数组，空表示跳过 magic bytes 校验）
     * @param allowedMimeTypes 允许的 MIME 类型集合（小写）
     * @param rule 该内容类型的上限规则
     * @return 识别出的视频格式（MP4 / WEBM）
     */
    public static String validate(FileAsset asset, byte[] head, Set<String> allowedMimeTypes, UploadRule rule) {
        if (asset == null || !asset.available()) {
            throw VideoErrorCode.exception(VideoErrorCode.UPLOAD_FILE_INVALID);
        }
        String mime = asset.contentType() == null ? "" : asset.contentType().toLowerCase(Locale.ROOT);
        if (!allowedMimeTypes.contains(mime)) {
            throw VideoErrorCode.exception(VideoErrorCode.VIDEO_FORMAT_NOT_ALLOWED);
        }
        if (asset.sizeBytes() <= 0 || asset.sizeBytes() > rule.maxBytes()) {
            throw VideoErrorCode.exception(VideoErrorCode.UPLOAD_FILE_TOO_LARGE);
        }
        String detected = detectFormat(head);
        if (detected != null && !mimeCompatible(mime, detected)) {
            throw VideoErrorCode.exception(VideoErrorCode.VIDEO_FORMAT_NOT_ALLOWED);
        }
        return detected == null ? formatOf(mime) : detected;
    }

    /**
     * 校验封面 / 海报图片是否符合图片白名单。
     *
     * @param asset 文件元数据
     * @param allowedMimeTypes 允许的图片 MIME 类型集合（小写）
     */
    public static void validateImage(FileAsset asset, Set<String> allowedMimeTypes) {
        if (asset == null || !asset.available()) {
            throw VideoErrorCode.exception(VideoErrorCode.UPLOAD_FILE_INVALID);
        }
        String mime = asset.contentType() == null ? "" : asset.contentType().toLowerCase(Locale.ROOT);
        if (!allowedMimeTypes.contains(mime)) {
            throw VideoErrorCode.exception(VideoErrorCode.IMAGE_FORMAT_NOT_ALLOWED);
        }
    }

    /**
     * 按文件头字节识别视频容器格式。
     *
     * @param head 文件头字节
     * @return {@code MP4} / {@code WEBM}；无法识别返回 {@code null}
     */
    public static String detectFormat(byte[] head) {
        if (head == null || head.length == 0) {
            return null;
        }
        if (head.length >= WEBM_HEAD_LENGTH
                && (head[0] & 0xFF) == WEBM_MAGIC_1 && (head[1] & 0xFF) == WEBM_MAGIC_2
                && (head[2] & 0xFF) == WEBM_MAGIC_3 && (head[3] & 0xFF) == WEBM_MAGIC_4) {
            return "WEBM";
        }
        if (head.length >= MP4_HEAD_MIN_LENGTH
                && isFtyp(head, MP4_FTYP_OFFSET)) {
            return "MP4";
        }
        return null;
    }

    /**
     * 判断 MIME 与识别格式是否兼容。
     *
     * @param mime 声明 MIME（小写）
     * @param detected 识别格式
     * @return 兼容返回 {@code true}
     */
    private static boolean mimeCompatible(String mime, String detected) {
        if ("MP4".equals(detected)) {
            return mime.contains("mp4") || mime.contains("quicktime");
        }
        return mime.contains("webm") || mime.contains("matroska");
    }

    /**
     * 由 MIME 推断格式名。
     *
     * @param mime 声明 MIME（小写）
     * @return 格式名
     */
    private static String formatOf(String mime) {
        return mime.contains("webm") ? "WEBM" : "MP4";
    }

    /**
     * 判断指定偏移是否为 {@code ftyp} 标识。
     *
     * @param head 文件头字节
     * @param offset 偏移
     * @return 是返回 {@code true}
     */
    private static boolean isFtyp(byte[] head, int offset) {
        return head[offset] == 'f' && head[offset + 1] == 't'
                && head[offset + 2] == 'y' && head[offset + 3] == 'p';
    }
}
