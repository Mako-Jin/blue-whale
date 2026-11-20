package com.bluewhale.file.service.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.content.FileMagicType;
import com.bluewhale.file.domain.content.FileSignatureInspector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * 上传内容安全策略：按文件头魔数识别真实类型，拦截可执行文件与扩展名伪装。
 *
 * <p>判定只依赖内容字节，不信任客户端声明的 {@code Content-Type}（浏览器与各类 SDK 常统一填
 * {@code application/octet-stream}，用于一致性校验会大量误伤）。扩展名作为「声明类型」与识别结果比对，
 * 扩展名无映射或内容无法识别时一律放行，避免自定义格式与加密文件被误伤。</p>
 *
 * @author BlueWhale
 */
@Component
public class FileContentPolicy {

    /** 扩展名到内容类型的映射：同一容器对应多个来源扩展名 */
    private static final Map<String, FileMagicType> EXTENSION_TYPES = createExtensionTypes();

    private final FileProperties fileProperties;

    /**
     * 构造内容安全策略
     *
     * @param fileProperties 文件服务配置（读取 {@code whale.file.security.*}）
     */
    public FileContentPolicy(FileProperties fileProperties) {
        this.fileProperties = fileProperties;
    }

    /**
     * 校验上传内容与声明扩展名是否匹配
     *
     * @param originalName 原始文件名，可为 {@code null} 或无扩展名
     * @param header 文件前若干字节，可为 {@code null}（视为无法识别）
     * @throws BizException 识别为禁止类型或与扩展名不符时抛出 {@code PARAM_ERROR}
     */
    public void validate(String originalName, byte[] header) {
        if (!fileProperties.getSecurity().isMagicEnabled()) {
            return;
        }
        FileMagicType detected = FileSignatureInspector.detect(header);
        if (detected == FileMagicType.UNKNOWN) {
            return;
        }
        if (isBlocked(detected)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "file.content.executable-blocked",
                    "禁止上传可执行文件: " + detected.getDescription(), detected.getDescription());
        }
        FileMagicType expected = expectedOf(originalName);
        if (expected != null && expected != detected) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "file.content.extension-mismatch",
                    "文件内容与扩展名不匹配: " + detected.getDescription(), detected.getDescription());
        }
    }

    /**
     * 判定识别结果是否属于配置的禁止类型
     *
     * @param detected 识别出的内容类型
     * @return 命中禁止类型时返回 {@code true}
     */
    private boolean isBlocked(FileMagicType detected) {
        for (String blocked : fileProperties.getSecurity().getBlockedTypes()) {
            if (blocked != null && blocked.trim().equalsIgnoreCase(detected.name())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按扩展名解析声明的期望内容类型
     *
     * <p>不可变映射不接受 {@code null} 键，无扩展名时必须在查询前短路，
     * 否则会抛 {@code NullPointerException} 而不是按「无声明」放行。</p>
     *
     * @param originalName 原始文件名，可为 {@code null}
     * @return 期望的内容类型；无扩展名或无映射时返回 {@code null}
     */
    private static FileMagicType expectedOf(String originalName) {
        String extension = extensionOf(originalName);
        return extension == null ? null : EXTENSION_TYPES.get(extension);
    }

    /**
     * 提取小写扩展名
     *
     * @param originalName 原始文件名，可为 {@code null}
     * @return 小写扩展名；无扩展名时返回 {@code null}
     */
    private static String extensionOf(String originalName) {
        String extension = StringUtils.getFilenameExtension(originalName);
        return extension == null ? null : extension.toLowerCase(Locale.ROOT);
    }

    /**
     * 构造扩展名到内容类型的映射
     *
     * @return 不可变映射
     */
    private static Map<String, FileMagicType> createExtensionTypes() {
        return Map.ofEntries(
                Map.entry("pdf", FileMagicType.PDF),
                Map.entry("png", FileMagicType.PNG),
                Map.entry("jpg", FileMagicType.JPEG),
                Map.entry("jpeg", FileMagicType.JPEG),
                Map.entry("gif", FileMagicType.GIF),
                Map.entry("zip", FileMagicType.ZIP),
                Map.entry("docx", FileMagicType.ZIP),
                Map.entry("xlsx", FileMagicType.ZIP),
                Map.entry("pptx", FileMagicType.ZIP),
                Map.entry("doc", FileMagicType.OLE2),
                Map.entry("xls", FileMagicType.OLE2),
                Map.entry("ppt", FileMagicType.OLE2),
                Map.entry("mp4", FileMagicType.MP4),
                Map.entry("mp3", FileMagicType.MP3),
                Map.entry("gz", FileMagicType.GZIP),
                Map.entry("rar", FileMagicType.RAR),
                Map.entry("7z", FileMagicType.SEVEN_ZIP));
    }
}
