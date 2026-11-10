package com.bluewhale.crm.customer.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CustomerCsv} 单元测试：RFC4180-lite 解析、必填与等级校验、导出脱敏。
 *
 * @author BlueWhale
 */
class CustomerCsvParserTest {

    @Test
    @DisplayName("解析：支持 BOM、双引号包裹、引号内逗号与 CRLF")
    void shouldParseQuotedCommaAndBom() {
        String content = "\uFEFF\"客户名称\",简称,备注\r\n"
                + "\"蓝鲸,科技有限公司\",\"鲸鱼\",\"含 \"\"引号\"\"\"\r\n";

        List<CustomerCsv.Row> rows = CustomerCsv.parse(content);

        assertEquals(1, rows.size());
        assertEquals("蓝鲸,科技有限公司", rows.get(0).getName());
        assertEquals("鲸鱼", rows.get(0).getShortName());
    }

    @Test
    @DisplayName("解析：缺少「客户名称」列直接拒绝")
    void shouldRejectMissingNameHeader() {
        BizException exception = assertThrows(BizException.class,
                () -> CustomerCsv.parse("简称,等级\n鲸鱼,A\n"));
        assertEquals(CrmErrorCode.CUSTOMER_IMPORT_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("校验：客户名称必填")
    void shouldRejectBlankName() {
        CustomerCsv.Row row = new CustomerCsv.Row();
        row.setName("  ");

        List<String> errors = CustomerCsv.validate(row);

        assertTrue(errors.contains("客户名称不能为空"));
    }

    @Test
    @DisplayName("校验：等级只允许 A/B/C/D")
    void shouldRejectIllegalLevel() {
        CustomerCsv.Row row = new CustomerCsv.Row();
        row.setName("蓝鲸科技");
        row.setLevel("Z");

        List<String> errors = CustomerCsv.validate(row);

        assertTrue(errors.stream().anyMatch(error -> error.contains("客户等级非法")));
    }

    @Test
    @DisplayName("导出：固定列顺序，手机 / 邮箱 / 税号按授权脱敏")
    void shouldMaskSensitiveFieldsOnExport() {
        CustomerCsv.Row row = exportRow();

        String masked = CustomerCsv.export(List.of(row), false);
        String plain = CustomerCsv.export(List.of(row), true);

        assertTrue(masked.contains("138****8000"));
        assertTrue(masked.contains("a****@x.com"));
        assertTrue(masked.contains("9133****BCDE"));
        assertFalse(masked.contains("13800138000"));
        assertEquals("客户编码,客户名称,简称,行业,来源,等级,省,市,区,税号,官网,标签,负责人,手机,邮箱,创建时间",
                masked.substring(0, masked.indexOf('\n')));
        assertTrue(plain.contains("13800138000"));
    }

    private static CustomerCsv.Row exportRow() {
        CustomerCsv.Row row = new CustomerCsv.Row();
        row.setCustomerCode("CUS-2026-000001");
        row.setName("蓝鲸科技");
        row.setPhone("13800138000");
        row.setEmail("a@x.com");
        row.setTaxNo("91330100MA2ABCDE");
        return row;
    }
}
