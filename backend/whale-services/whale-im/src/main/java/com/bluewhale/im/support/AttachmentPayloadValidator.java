package com.bluewhale.im.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 附件消息内容校验与归一化。
 *
 * <p>图片与文件消息不承载文件本体：客户端先上传到 {@code whale-file}，再把上传结果的
 * {@code fileId}、文件名、大小、MIME 以 JSON 引用形式写入消息 {@code content}。
 * 本类只放行白名单字段并重新序列化，避免任意 JSON 落库。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class AttachmentPayloadValidator {

    /** 内容类型：图片 */
    private static final String TYPE_IMAGE = "IMAGE";

    /** 内容类型：文件 */
    private static final String TYPE_FILE = "FILE";

    /** fileId 最大长度 */
    private static final int MAX_FILE_ID_LENGTH = 64;

    /** 文件名最大长度 */
    private static final int MAX_FILE_NAME_LENGTH = 255;

    /** MIME 类型最大长度 */
    private static final int MAX_MIME_LENGTH = 128;

    private final ObjectMapper objectMapper;

    /**
     * 校验并归一化附件内容；非附件类型原样返回。
     *
     * @param contentType 内容类型
     * @param content     原始内容
     * @return 归一化后的 JSON 文本（仅白名单字段）
     */
    public String validateAndNormalize(String contentType, String content) {
        if (!TYPE_IMAGE.equals(contentType) && !TYPE_FILE.equals(contentType)) {
            return content;
        }
        JsonNode node = parse(content);
        String fileId = text(node, "fileId");
        if (!StringUtils.hasText(fileId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "附件消息缺少 fileId");
        }
        if (fileId.length() > MAX_FILE_ID_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR, "附件 fileId 非法");
        }
        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("fileId", fileId);
        putText(normalized, "fileName", text(node, "fileName"), MAX_FILE_NAME_LENGTH);
        putText(normalized, "contentType", text(node, "contentType"), MAX_MIME_LENGTH);
        Long fileSize = number(node, "fileSize");
        if (fileSize != null) {
            if (fileSize < 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "附件大小非法");
            }
            normalized.put("fileSize", fileSize);
        }
        Long width = number(node, "width");
        if (width != null && width > 0) {
            normalized.put("width", width);
        }
        Long height = number(node, "height");
        if (height != null && height > 0) {
            normalized.put("height", height);
        }
        return objectMapper.writeValueAsString(normalized);
    }

    /**
     * 解析附件内容为 JSON 对象。
     *
     * @param content 原始内容
     * @return JSON 对象节点
     */
    private JsonNode parse(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "附件消息缺少文件引用");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(content);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "附件消息内容必须为 JSON 对象");
        }
        if (node == null || !node.isObject()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "附件消息内容必须为 JSON 对象");
        }
        return node;
    }

    /**
     * 读取文本字段。
     *
     * @param node  JSON 对象
     * @param field 字段名
     * @return 字段文本；缺失或空返回 {@code null}
     */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    /**
     * 读取数值字段（兼容数字与数字字符串）。
     *
     * @param node  JSON 对象
     * @param field 字段名
     * @return 字段数值；缺失或非法返回 {@code null}
     */
    private static Long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asLong();
    }

    /**
     * 写入受限长度的文本字段。
     *
     * @param target   目标对象
     * @param field    字段名
     * @param value    字段值
     * @param maxLength 最大长度
     */
    private static void putText(ObjectNode target, String field, String value, int maxLength) {
        if (value == null) {
            return;
        }
        target.put(field, value.length() > maxLength ? value.substring(0, maxLength) : value);
    }
}
