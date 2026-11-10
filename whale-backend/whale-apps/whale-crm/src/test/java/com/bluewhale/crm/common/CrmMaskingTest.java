package com.bluewhale.crm.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link CrmMasking} 单元测试（REQ-SEC-034）。
 *
 * @author BlueWhale
 */
class CrmMaskingTest {

    @Test
    @DisplayName("手机号：保留前 3 后 4，短号整段隐藏")
    void shouldMaskPhone() {
        assertEquals("138****8000", CrmMasking.phone("13800138000"));
        assertEquals("1****", CrmMasking.phone("1234567"));
        assertEquals("****", CrmMasking.phone("1234"));
        assertNull(CrmMasking.phone(null));
        assertEquals(" ", CrmMasking.phone(" "));
    }

    @Test
    @DisplayName("邮箱：保留首字符与域名")
    void shouldMaskEmail() {
        assertEquals("a****@b.com", CrmMasking.email("abc@b.com"));
        assertEquals("****", CrmMasking.email("not-an-email"));
        assertNull(CrmMasking.email(null));
    }

    @Test
    @DisplayName("税号：保留前后各 4 位，过短整段隐藏")
    void shouldMaskTaxNo() {
        assertEquals("9133****BCDE", CrmMasking.taxNo("91330100MA2ABCDE"));
        assertEquals("****", CrmMasking.taxNo("12345678"));
        assertNull(CrmMasking.taxNo(null));
    }

    @Test
    @DisplayName("微信号：保留前后各 2 位")
    void shouldMaskWechat() {
        assertEquals("wx****id", CrmMasking.wechat("wxid_abc_id", false));
        assertEquals("****", CrmMasking.wechat("wxid", false));
        assertEquals("wxid_abc_id", CrmMasking.wechat("wxid_abc_id", true));
        assertNull(CrmMasking.wechat(null, false));
    }

    @Test
    @DisplayName("敏感备注：无权限整体隐藏")
    void shouldMaskNote() {
        assertEquals("******", CrmMasking.note("客户要求账期 60 天", false));
        assertEquals("客户要求账期 60 天", CrmMasking.note("客户要求账期 60 天", true));
        assertNull(CrmMasking.note(null, false));
    }

    @Test
    @DisplayName("按权限放行明文")
    void shouldAllowPlainWhenPermitted() {
        assertEquals("13800138000", CrmMasking.phone("13800138000", true));
        assertEquals("a@b.com", CrmMasking.email("a@b.com", true));
        assertEquals("91330100MA2ABCDE", CrmMasking.taxNo("91330100MA2ABCDE", true));
        assertEquals("138****8000", CrmMasking.phone("13800138000", false));
    }
}
