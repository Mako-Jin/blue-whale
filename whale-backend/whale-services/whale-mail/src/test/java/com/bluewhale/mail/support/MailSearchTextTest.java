package com.bluewhale.mail.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 检索词处理单测。
 *
 * <p>重点覆盖三类容易出错的地方：中文短语必须整体包裹（否则 ngram 词项会散落命中）、
 * 用户输入里的 BOOLEAN MODE 元字符必须剥离（否则 MySQL 直接语法报错）、
 * 单字必须判定为「索引搜不到」以便上层回退 LIKE。</p>
 *
 * @author BlueWhale
 */
class MailSearchTextTest {

    /**
     * 归一化：折叠空白并去掉全角空格
     */
    @Test
    @DisplayName("归一化折叠空白")
    void shouldNormalizeWhitespace() {
        assertEquals("季度 报告", MailSearchText.normalize("  季度\u3000 报告  "));
        assertEquals("", MailSearchText.normalize(null));
    }

    /**
     * 超长检索词截断，避免把整段正文当查询串
     */
    @Test
    @DisplayName("超长检索词截断")
    void shouldTruncateLongKeyword() {
        String raw = "a".repeat(500);
        assertEquals(120, MailSearchText.normalize(raw).length());
    }

    /**
     * 单字判定：ngram 默认 2-gram，单字必然搜不到
     */
    @Test
    @DisplayName("单字判定为过短")
    void shouldTreatSingleCharAsTooShort() {
        assertTrue(MailSearchText.isTooShort("票"));
        assertTrue(MailSearchText.isTooShort(""));
        assertFalse(MailSearchText.isTooShort("发票"));
    }

    /**
     * 中文词条包成短语，英文词条不加引号
     */
    @Test
    @DisplayName("中文短语包裹与英文直连")
    void shouldBuildBooleanExpression() {
        assertEquals("+\"季度报告\"", MailSearchText.toBooleanExpr("季度报告"));
        assertEquals("+\"季度\" +\"报告\"", MailSearchText.toBooleanExpr("季度 报告"));
        assertEquals("+invoice", MailSearchText.toBooleanExpr("invoice"));
        assertEquals("+invoice +\"付款\"", MailSearchText.toBooleanExpr("invoice 付款"));
    }

    /**
     * BOOLEAN MODE 元字符必须剥离，否则是 SQL 语法错误而不是「搜不到」
     */
    @Test
    @DisplayName("剥离 BOOLEAN MODE 元字符")
    void shouldStripBooleanMeta() {
        assertEquals("+\"季度报告\"", MailSearchText.toBooleanExpr("\"+季度报告\""));
        assertNull(MailSearchText.toBooleanExpr("+-~*"));
        assertNull(MailSearchText.toBooleanExpr("   "));
    }

    /**
     * 标点与空白混合输入能切出词条
     */
    @Test
    @DisplayName("按标点切分词条")
    void shouldSplitTerms() {
        List<String> terms = MailSearchText.terms("发票，报销；2026");
        assertEquals(List.of("发票", "报销", "2026"), terms);
    }

    /**
     * LIKE 兜底模式串转义通配符：用户输入 % 不应该变成「匹配一切」
     */
    @Test
    @DisplayName("LIKE 模式串转义")
    void shouldEscapeLikePattern() {
        assertEquals("%100\\%完成%", MailSearchText.toLikePattern("100%完成"));
        assertEquals("%a\\_b%", MailSearchText.toLikePattern("a_b"));
        assertNull(MailSearchText.toLikePattern(" "));
    }
}
