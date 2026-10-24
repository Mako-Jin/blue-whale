package com.bluewhale.im.support;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AttachmentPayloadValidator} 单元测试：白名单归一化与非法内容拒绝。
 *
 * @author BlueWhale
 */
class AttachmentPayloadValidatorTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final AttachmentPayloadValidator validator = new AttachmentPayloadValidator(objectMapper);

    @Test
    @DisplayName("非附件类型原样返回")
    void shouldPassThroughTextContent() {
        assertEquals("你好", validator.validateAndNormalize("TEXT", "你好"));
    }

    @Test
    @DisplayName("图片消息：只保留白名单字段并归一化 fileId")
    void shouldNormalizeImageContent() {
        String content = "{\"fileId\":123,\"fileName\":\"a.png\",\"fileSize\":2048,"
                + "\"contentType\":\"image/png\",\"width\":100,\"height\":200,\"extra\":\"drop-me\"}";

        String normalized = validator.validateAndNormalize("IMAGE", content);

        assertTrue(normalized.contains("\"fileId\":\"123\""));
        assertTrue(normalized.contains("\"fileName\":\"a.png\""));
        assertTrue(normalized.contains("\"width\":100"));
        assertTrue(!normalized.contains("extra"));
    }

    @Test
    @DisplayName("文件消息：缺少 fileId 时拒绝")
    void shouldRejectMissingFileId() {
        assertThrows(BizException.class,
                () -> validator.validateAndNormalize("FILE", "{\"fileName\":\"a.pdf\"}"));
    }

    @Test
    @DisplayName("附件内容不是 JSON 对象时拒绝")
    void shouldRejectNonObjectContent() {
        assertThrows(BizException.class, () -> validator.validateAndNormalize("IMAGE", "not-json"));
        assertThrows(BizException.class, () -> validator.validateAndNormalize("IMAGE", "[1,2]"));
    }

    @Test
    @DisplayName("附件大小为负数时拒绝")
    void shouldRejectNegativeFileSize() {
        assertThrows(BizException.class,
                () -> validator.validateAndNormalize("FILE", "{\"fileId\":\"9\",\"fileSize\":-1}"));
    }

    @Test
    @DisplayName("fileId 超长时拒绝")
    void shouldRejectTooLongFileId() {
        String fileId = "a".repeat(65);

        assertThrows(BizException.class,
                () -> validator.validateAndNormalize("FILE", "{\"fileId\":\"" + fileId + "\"}"));
    }
}
