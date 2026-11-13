package com.bluewhale.common.core.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分页响应体构建测试：字段原样映射、空页与泛型保留。
 */
class PageResultTest {

    @Test
    @DisplayName("of(records,total,current,size)：四个字段原样写入")
    void shouldMapAllFields() {
        List<String> records = List.of("a", "b");

        PageResult<String> result = PageResult.of(records, 42L, 3L, 20L);

        assertSame(records, result.getRecords());
        assertEquals(42L, result.getTotal());
        assertEquals(3L, result.getCurrent());
        assertEquals(20L, result.getSize());
    }

    @Test
    @DisplayName("空页：records 为空列表且 total 为 0")
    void shouldAllowEmptyPage() {
        PageResult<String> result = PageResult.of(List.of(), 0L, 1L, 10L);

        assertTrue(result.getRecords().isEmpty());
        assertEquals(0L, result.getTotal());
        assertEquals(1L, result.getCurrent());
    }
}
