package com.bluewhale.file.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link FileStatus} 状态码映射单元测试。
 *
 * @author BlueWhale
 */
class FileStatusTest {

    @Test
    @DisplayName("fromCode：已知状态码正确还原，缺失或未知按正常处理")
    void shouldMapStatusCodes() {
        assertEquals(FileStatus.DISABLED, FileStatus.fromCode(0));
        assertEquals(FileStatus.NORMAL, FileStatus.fromCode(1));
        assertEquals(FileStatus.LOCKED, FileStatus.fromCode(2));
        assertEquals(FileStatus.NORMAL, FileStatus.fromCode(null));
        assertEquals(FileStatus.NORMAL, FileStatus.fromCode(9));
    }

    @Test
    @DisplayName("getCode/getDescription：回传落库值与描述")
    void shouldExposeCodeAndDescription() {
        assertEquals(2, FileStatus.LOCKED.getCode());
        assertEquals("归档锁定", FileStatus.LOCKED.getDescription());
        assertEquals(3, FileStatus.values().length);
    }
}
