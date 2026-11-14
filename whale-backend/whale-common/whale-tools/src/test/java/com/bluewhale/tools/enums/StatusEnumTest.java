package com.bluewhale.tools.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link StatusEnum} 单元测试：枚举项与 code/desc 映射。
 *
 * @author BlueWhale
 */
class StatusEnumTest {

    @Test
    @DisplayName("枚举项：启用/禁用 的 code 与 desc 符合约定")
    void shouldExposeCodeAndDesc() {
        assertEquals(Integer.valueOf(1), StatusEnum.ENABLED.getCode());
        assertEquals("启用", StatusEnum.ENABLED.getDesc());

        assertEquals(Integer.valueOf(0), StatusEnum.DISABLED.getCode());
        assertEquals("禁用", StatusEnum.DISABLED.getDesc());
    }

    @Test
    @DisplayName("values/valueOf：两个枚举项可按键名还原")
    void shouldSupportValueOf() {
        assertEquals(2, StatusEnum.values().length);
        assertSame(StatusEnum.ENABLED, StatusEnum.valueOf("ENABLED"));
        assertSame(StatusEnum.DISABLED, StatusEnum.valueOf("DISABLED"));
    }
}
