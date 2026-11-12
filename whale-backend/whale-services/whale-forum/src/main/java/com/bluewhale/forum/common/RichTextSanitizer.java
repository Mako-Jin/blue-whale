package com.bluewhale.forum.common;

import java.util.regex.Pattern;

/**
 * 富文本白名单消毒（服务端强制，前端过滤只算优化）。
 *
 * <p>做法是「危险构造一律拒绝」而非「尝试清洗」：{@code script/iframe/object/embed/style}
 * 标签、{@code on*} 事件属性、{@code javascript:} 协议、{@code expression()} 出现即抛
 * {@link ForumErrorCodes#CONTENT_REJECTED}。理由——清洗式实现一旦漏一段正则，
 * 脏内容就落库并通过 REST 接口原样返回给所有人；拒绝式实现最坏是误伤，
 * 不会造成存储型 XSS。</p>
 *
 * <p>规格 §六 要求「消毒失败 30030」，故本类只做拦截与去噪，不做标签重写。</p>
 *
 * @author BlueWhale
 */
public final class RichTextSanitizer {

    /** 危险标签（不区分大小写，含自闭合与带属性写法） */
    private static final Pattern DANGEROUS_TAG = Pattern.compile(
            "<\\s*(script|iframe|object|embed|style|link|meta|base|form)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 危险协议 */
    private static final Pattern DANGEROUS_PROTOCOL = Pattern.compile(
            "(javascript|vbscript|data)\\s*:",
            Pattern.CASE_INSENSITIVE);

    /** 内联事件属性（onclick/onerror/onload…） */
    private static final Pattern INLINE_EVENT = Pattern.compile(
            "<[^>]*\\son[a-z]+\\s*=",
            Pattern.CASE_INSENSITIVE);

    /** CSS 表达式（老 IE 注入面，仍被部分扫描器判定为高危） */
    private static final Pattern CSS_EXPRESSION = Pattern.compile(
            "expression\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private RichTextSanitizer() {
    }

    /**
     * 消毒富文本，发现危险构造即拒绝。
     *
     * @param raw 原始富文本源码，可为 {@code null}
     * @return 去掉首尾空白后的安全文本；入参为 {@code null} 时返回 {@code null}
     * @throws com.bluewhale.common.core.exception.BizException 命中危险构造时抛出 30030
     */
    public static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.strip();
        if (DANGEROUS_TAG.matcher(text).find()
                || DANGEROUS_PROTOCOL.matcher(text).find()
                || INLINE_EVENT.matcher(text).find()
                || CSS_EXPRESSION.matcher(text).find()) {
            throw new com.bluewhale.common.core.exception.BizException(
                    ForumErrorCodes.CONTENT_REJECTED, "内容包含不允许的标签或脚本，请调整后重试");
        }
        return text;
    }

    /**
     * 由正文提取纯文本摘要（去标签 + 压缩空白 + 截断）。
     *
     * @param content 已消毒的富文本源码
     * @param maxLength 摘要最大长度（字符）
     * @return 摘要文本；入参为空时返回空串
     */
    public static String summarize(String content, int maxLength) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String plain = content.replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .strip();
        return plain.length() <= maxLength ? plain : plain.substring(0, maxLength);
    }
}
