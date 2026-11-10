package com.bluewhale.crm.customer.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link CustomerNormalizer} 单元测试：只做比较归一化，不改变原始录入值。
 *
 * @author BlueWhale
 */
class CustomerNormalizerTest {

    @Test
    @DisplayName("客户名称：剔除标点与公司通用后缀")
    void shouldNormalizeCustomerName() {
        assertEquals("蓝鲸科技", CustomerNormalizer.normalizeName("蓝鲸科技有限公司"));
        assertEquals("蓝鲸科技", CustomerNormalizer.normalizeName("蓝鲸科技股份有限公司"));
        assertEquals("蓝鲸科技杭州", CustomerNormalizer.normalizeName("蓝鲸科技（杭州）有限公司"));
        assertEquals("ab", CustomerNormalizer.normalizeName("A-B 有限公司"));
        assertEquals("蓝鲸科技", CustomerNormalizer.normalizeName("  蓝鲸科技  "));
    }

    @Test
    @DisplayName("客户名称：仅公司类型字样时剔除后保留剩余字样，空值返回 null")
    void shouldKeepBareSuffixAndReturnNullForBlank() {
        assertEquals("股份", CustomerNormalizer.normalizeName("股份有限公司"));
        assertNull(CustomerNormalizer.normalizeName(null));
        assertNull(CustomerNormalizer.normalizeName("   "));
        assertNull(CustomerNormalizer.normalizeName("--"));
    }

    @Test
    @DisplayName("官网域名：去掉协议、路径、端口与 www 前缀")
    void shouldNormalizeDomain() {
        assertEquals("example.com", CustomerNormalizer.normalizeDomain("https://www.Example.COM:8443/path/to"));
        assertEquals("example.com", CustomerNormalizer.normalizeDomain("http://example.com"));
        assertEquals("example.com", CustomerNormalizer.normalizeDomain("example.com/abc"));
        assertEquals("example.com", CustomerNormalizer.normalizeDomain("WWW.Example.com"));
        assertNull(CustomerNormalizer.normalizeDomain("https://"));
        assertNull(CustomerNormalizer.normalizeDomain(" "));
    }

    @Test
    @DisplayName("税号：大写并去掉空格与符号")
    void shouldNormalizeTaxNo() {
        assertEquals("91330100MA2ABCDE", CustomerNormalizer.normalizeTaxNo(" 91 3301 00MA2ABCDE "));
        assertEquals("91330100MA2ABCDE", CustomerNormalizer.normalizeTaxNo("91-3301-00ma2abcde"));
        assertNull(CustomerNormalizer.normalizeTaxNo("---"));
        assertNull(CustomerNormalizer.normalizeTaxNo(null));
    }

    @Test
    @DisplayName("电话：去掉国家码与分隔符")
    void shouldNormalizePhone() {
        assertEquals("13800138000", CustomerNormalizer.normalizePhone("+86 138-0013-8000"));
        assertEquals("13800138000", CustomerNormalizer.normalizePhone("0086 13800138000"));
        assertEquals("13800138000", CustomerNormalizer.normalizePhone("13800138000"));
        assertEquals("02188886666", CustomerNormalizer.normalizePhone("021-8888-6666"));
        assertNull(CustomerNormalizer.normalizePhone("abc"));
        assertNull(CustomerNormalizer.normalizePhone(null));
    }

    @Test
    @DisplayName("邮箱：去空格并转小写")
    void shouldNormalizeEmail() {
        assertEquals("foo@bar.com", CustomerNormalizer.normalizeEmail("  Foo@Bar.COM "));
        assertNull(CustomerNormalizer.normalizeEmail(" "));
        assertNull(CustomerNormalizer.normalizeEmail(null));
    }
}
