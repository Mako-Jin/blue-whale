package com.bluewhale.archive.support;

import com.bluewhale.archive.remote.RemoteFormField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表单附件递归提取器单元测试。
 *
 * @author BlueWhale
 */
class AttachmentExtractorTest {

    @Test
    @DisplayName("extract：附件支持数字、字符串、对象三种形态并去重")
    void shouldExtractAttachmentIds() {
        RemoteFormField attachment = field("attachments", ArchiveConstants.FORM_TYPE_ATTACHMENT);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("attachments", List.of(11, "12", Map.of("fileId", 13), 11, "bad", -1));

        List<Long> ids = AttachmentExtractor.extract(values, List.of(attachment));

        assertEquals(List.of(11L, 12L, 13L), ids);
    }

    @Test
    @DisplayName("extract：子表逐行递归子字段附件")
    void shouldExtractFromSubtableRows() {
        RemoteFormField files = field("files", ArchiveConstants.FORM_TYPE_ATTACHMENT);
        RemoteFormField row = field("items", ArchiveConstants.FORM_TYPE_SUBTABLE);
        row.setChildren(List.of(files));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("items", List.of(Map.of("files", List.of(21, 22)), Map.of("files", 21)));

        List<Long> ids = AttachmentExtractor.extract(values, List.of(row));

        assertEquals(List.of(21L, 22L), ids);
    }

    @Test
    @DisplayName("extract：空值与无附件字段返回空列表")
    void shouldReturnEmptyWhenNoAttachment() {
        RemoteFormField text = field("name", "text");

        assertTrue(AttachmentExtractor.extract(null, List.of(text)).isEmpty());
        assertTrue(AttachmentExtractor.extract(Map.of("name", "x"), List.of(text)).isEmpty());
        assertTrue(AttachmentExtractor.extract(Map.of("name", "x"), null).isEmpty());
    }

    /**
     * 构造字段定义。
     *
     * @param key 字段 key
     * @param type 字段类型
     * @return 字段定义
     */
    private static RemoteFormField field(String key, String type) {
        RemoteFormField field = new RemoteFormField();
        field.setKey(key);
        field.setType(type);
        return field;
    }
}
