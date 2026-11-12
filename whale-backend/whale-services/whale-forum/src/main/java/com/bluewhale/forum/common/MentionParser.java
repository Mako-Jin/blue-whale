package com.bluewhale.forum.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @提及解析器（纯函数，无 Spring 依赖，便于单测）。
 *
 * <p><b>输入必须是纯文本</b>（由 {@link RichTextSanitizer#summarize} 从消毒后的富文本提取）。
 * 直接解析 HTML 会把 {@code @张三</p>} 里的标签算进昵称，也会让 {@code <a href="mailto:a@b">}
 * 这种属性里的 {@code @} 变成一次提及。</p>
 *
 * <p><b>歧义处理</b>：「@张三的说法」在中文里既可能是 @张三 也可能是一个叫「张三的说法」的人。
 * 本类按<b>最长匹配</b>取「张三的说法」；解析不到就跳过该提及，绝不猜测。
 * 前端应当用 @ 选择器插入「@昵称 」并带尾随空格，从源头消除歧义。</p>
 *
 * <p><b>为什么排除邮箱</b>：{@code zhangsan@example.com} 的 {@code @} 前面是字母，
 * 正则用后行断言排除「前一个字符是字母/数字/下划线/点」的情形。</p>
 *
 * @author BlueWhale
 */
public final class MentionParser {

    /** 全角 @：中文输入法下极易打出，不做归一化会静默漏掉一半提及 */
    private static final char FULLWIDTH_AT = '＠';

    /**
     * 提及模式：{@code @} + 1~32 个「中文/字母/数字/下划线/点/连字符」。
     *
     * <p>后行断言排除邮箱前缀；前面的字符类包含点是为了 {@code foo.bar@x.com} 也不误判。</p>
     */
    private static final Pattern PATTERN = Pattern.compile(
            "(?<![\\p{L}\\p{N}_.-])@([\\p{L}\\p{N}_.-]{1,32})");

    /** 昵称尾部可能粘上的 ASCII 标点（句号/逗号/问号/叹号/斜杠/反斜杠） */
    private static final String TRIMMABLE_TAIL = ".,?!:;/\\";

    private MentionParser() {
    }

    /**
     * 解析提及结果。
     *
     * @param names 提及的昵称（按首次出现顺序去重后）
     * @param truncated 是否因超出上限被截断
     */
    public record Result(List<String> names, boolean truncated) {

        /** 空结果 */
        public static final Result EMPTY = new Result(List.of(), false);
    }

    /**
     * 解析纯文本中的 @提及。
     *
     * @param plainText 纯文本（可空）
     * @param max 上限（≤0 视为不限制）
     * @return 解析结果；无提及时返回 {@link Result#EMPTY}
     */
    public static Result parse(String plainText, int max) {
        if (plainText == null || plainText.isEmpty()) {
            return Result.EMPTY;
        }
        String normalized = plainText.indexOf(FULLWIDTH_AT) >= 0
                ? plainText.replace(FULLWIDTH_AT, '@')
                : plainText;
        Matcher matcher = PATTERN.matcher(normalized);
        Set<String> unique = new LinkedHashSet<>();
        while (matcher.find()) {
            String name = trimTail(matcher.group(1));
            if (!name.isEmpty()) {
                unique.add(name);
            }
        }
        if (unique.isEmpty()) {
            return Result.EMPTY;
        }
        List<String> names = new ArrayList<>(unique);
        if (max > 0 && names.size() > max) {
            return new Result(List.copyOf(names.subList(0, max)), true);
        }
        return new Result(List.copyOf(names), false);
    }

    /**
     * 去掉昵称尾部误吞的 ASCII 标点。
     *
     * @param name 原始匹配
     * @return 修剪后的昵称
     */
    private static String trimTail(String name) {
        int end = name.length();
        while (end > 0 && TRIMMABLE_TAIL.indexOf(name.charAt(end - 1)) >= 0) {
            end--;
        }
        return name.substring(0, end);
    }
}
