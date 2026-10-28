package com.bluewhale.file.domain.storage;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 分片上传会话标识值对象：构造即校验为标准 UUID，避免调用方传入任意字符串造成临时目录穿越。
 *
 * @param value 会话标识（标准 UUID 字符串，统一小写）
 * @author BlueWhale
 */
public record ChunkUploadSessionId(String value) {

    /** 标准 UUID 文本格式：8-4-4-4-12 位十六进制 */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /**
     * 紧凑构造器：校验会话标识为标准 UUID 并归一化为小写
     *
     * @param value 会话标识
     * @throws BizException 非标准 UUID 时抛出 {@code PARAM_ERROR}
     */
    public ChunkUploadSessionId {
        if (value == null || !UUID_PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "uploadId 非法");
        }
        value = value.toLowerCase(Locale.ROOT);
    }

    /**
     * 由调用方传入的字符串创建会话标识，非标准 UUID 时拒绝
     *
     * @param raw 调用方传入的 uploadId
     * @return 分片上传会话标识
     * @throws BizException 非标准 UUID 时抛出 {@code PARAM_ERROR}
     */
    public static ChunkUploadSessionId of(String raw) {
        return new ChunkUploadSessionId(raw);
    }

    /**
     * 生成新的随机会话标识
     *
     * @return 分片上传会话标识
     */
    public static ChunkUploadSessionId random() {
        return new ChunkUploadSessionId(UUID.randomUUID().toString());
    }
}
