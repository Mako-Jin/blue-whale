package com.bluewhale.contract.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 合同状态枚举单元测试（覆盖率门禁补齐）。
 *
 * <p>校验状态文本解析的空值兜底与非法值语义。</p>
 *
 * @author BlueWhale
 */
class ContractStatusTest {

    /**
     * 合法状态文本解析为对应枚举。
     */
    @Test
    void shouldParseKnownStatus() {
        assertEquals(ContractStatus.DRAFT, ContractStatus.of("DRAFT"));
        assertEquals(ContractStatus.APPROVING, ContractStatus.of("APPROVING"));
        assertEquals(ContractStatus.ARCHIVED, ContractStatus.of("ARCHIVED"));
    }

    /**
     * 空值与非法状态文本返回 {@code null}。
     */
    @Test
    void shouldReturnNullForUnknownStatus() {
        assertNull(ContractStatus.of(null));
        assertNull(ContractStatus.of("UNKNOWN"));
        assertNull(ContractStatus.of("draft"));
    }
}
