package com.bluewhale.mail.support;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索词处理：把用户输入转成各引擎能用的查询表达式。
 *
 * <p>两种引擎的语法差异必须在这里吃掉，不能漏给调用方：MySQL 用 BOOLEAN MODE
 * 的 {@code +"短语"}，Lucene 用 QueryParser 的 {@code +field:词}；而用户只会输入
 * 「季度 报告」这样一串词。集中在这里的好处是同一份输入在两套引擎下语义一致——
 * 多词一律按 AND 处理，符合「搜索」而非「推荐」的直觉。</p>
 *
 * @author BlueWhale
 */
public final class MailSearchText {

    /** 检索词最大长度：超长输入会给分词器和 SQL 都带来无意义开销 */
    private static final int MAX_KEYWORD_LENGTH = 120;

    /** MySQL BOOLEAN MODE 的元字符，出现在用户输入里必须剥离，否则查询语法报错 */
    private static final String BOOLEAN_META = "+-<>()~*\"@";

    /**
     * 工具类构造器（禁止实例化）
     */
    private MailSearchText() {
    }

    /**
     * 归一化检索词：去首尾空白、折叠连续空白、截断超长输入。
     *
     * @param raw 原始输入
     * @return 归一化结果；空输入返回空串
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace('\u3000', ' ').trim().replaceAll("\\s+", " ");
        return text.length() <= MAX_KEYWORD_LENGTH ? text : text.substring(0, MAX_KEYWORD_LENGTH);
    }

    /**
     * 判断检索词是否短到 ngram 索引无法命中。
     *
     * <p>ngram 默认 {@code ngram_token_size=2}，单个汉字或单字符都切不出一个词项，
     * 全文索引必然返回空。这类查询只能走 LIKE，判断放在这里而不是引擎实现里，
     * 是为了让两个引擎对「短词」的处理保持同一口径。</p>
     *
     * @param keyword 归一化后的检索词
     * @return 短词返回 {@code true}
     */
    public static boolean isTooShort(String keyword) {
        return keyword == null || keyword.codePointCount(0, keyword.length()) < 2;
    }

    /**
     * 拆出可用于检索的词条（按空白与常见标点切分）。
     *
     * @param keyword 归一化后的检索词
     * @return 词条列表（可能为空）
     */
    public static List<String> terms(String keyword) {
        List<String> terms = new ArrayList<>();
        if (keyword == null || keyword.isBlank()) {
            return terms;
        }
        for (String part : keyword.split("[\\s,，。、;；:：!！?？()（）\\[\\]【】\"'“”]+")) {
            String term = part.trim();
            if (!term.isEmpty() && !terms.contains(term)) {
                terms.add(term);
            }
        }
        return terms;
    }

    /**
     * 构造 MySQL BOOLEAN MODE 表达式。
     *
     * <p>含中文的词条用双引号包成短语：ngram 会把「季度报告」切成
     * {@code 季度 / 度报 / 报告}，不包短语时三个词项可能散落在全文任意位置，
     * 出现「搜季度报告却命中只提到季度和报告各一次的两段话」。包成短语后
     * MySQL 要求词项按序连续，语义才对得上。</p>
     *
     * <p>纯 ASCII 词条不加引号：英文单词没有 ngram 切分问题，加引号只会
     * 把「invoice 和 paid 之间隔了几个词」这种正常情况排除掉。</p>
     *
     * @param keyword 归一化后的检索词
     * @return 表达式；无可用词条返回 {@code null}
     */
    public static String toBooleanExpr(String keyword) {
        List<String> terms = terms(keyword);
        if (terms.isEmpty()) {
            return null;
        }
        StringBuilder expr = new StringBuilder();
        for (String term : terms) {
            String cleaned = stripBooleanMeta(term);
            if (cleaned.isEmpty()) {
                continue;
            }
            if (expr.length() > 0) {
                expr.append(' ');
            }
            expr.append('+');
            if (isAsciiWord(cleaned)) {
                expr.append(cleaned);
            } else {
                expr.append('"').append(cleaned).append('"');
            }
        }
        return expr.length() == 0 ? null : expr.toString();
    }

    /**
     * 生成 LIKE 兜底用的模式串（转义通配符）。
     *
     * @param term 词条
     * @return 形如 {@code %词%} 的模式串；空词返回 {@code null}
     */
    public static String toLikePattern(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /**
     * 剥离 BOOLEAN MODE 元字符。
     *
     * @param term 词条
     * @return 剥离后的词条
     */
    private static String stripBooleanMeta(String term) {
        StringBuilder builder = new StringBuilder(term.length());
        for (int i = 0; i < term.length(); i++) {
            char c = term.charAt(i);
            if (BOOLEAN_META.indexOf(c) < 0) {
                builder.append(c);
            }
        }
        return builder.toString().trim();
    }

    /**
     * 判断是否为纯 ASCII 词（字母、数字及常见连接符）。
     *
     * @param term 词条
     * @return 纯 ASCII 返回 {@code true}
     */
    private static boolean isAsciiWord(String term) {
        for (int i = 0; i < term.length(); i++) {
            if (term.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }
}
