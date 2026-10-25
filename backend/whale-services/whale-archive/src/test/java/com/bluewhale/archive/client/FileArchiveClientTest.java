package com.bluewhale.archive.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 文件服务响应文件ID解析单元测试。
 *
 * <p>平台统一响应体会把 {@code Long} 序列化为字符串（精度保护），
 * 因此上传返回的文件ID必须同时兼容数字与数字字符串两种形态。</p>
 *
 * @author BlueWhale
 */
class FileArchiveClientTest {

    @Test
    @DisplayName("parseId：数字型文件ID直接转换")
    void shouldParseNumberId() {
        assertEquals(123L, FileArchiveClient.parseId(123L));
        assertEquals(456L, FileArchiveClient.parseId(456));
    }

    @Test
    @DisplayName("parseId：数字字符串（Long 序列化形态）可解析")
    void shouldParseStringId() {
        assertEquals(2114171016869560322L, FileArchiveClient.parseId("2114171016869560322"));
        assertEquals(7L, FileArchiveClient.parseId(" 7 "));
    }

    @Test
    @DisplayName("parseId：空值与非法值返回 null")
    void shouldReturnNullForInvalid() {
        assertNull(FileArchiveClient.parseId(null));
        assertNull(FileArchiveClient.parseId(""));
        assertNull(FileArchiveClient.parseId("   "));
        assertNull(FileArchiveClient.parseId("abc"));
    }
}
