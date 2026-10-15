package com.bluewhale.sts.common.db.query;

/**
 * 搜索关键词 → {@code LIKE} 条件：**把 {@code %} 与 {@code _} 当字面量**，且**把转义符写进 SQL**。
 *
 * <h2>为什么需要它</h2>
 *
 * 搜索框的关键词是原样拼进 {@code LIKE '%…%'} 的，而 MyBatis-Plus 的 {@code like()} 只做
 * {@code "%" + value + "%"}（实测其 {@code SqlUtils.concatLike} 的实现），**不转义**。
 * 于是用户在搜索框里输 {@code %} 会匹配**全部行**、输 {@code _} 会匹配任意单字符 ——
 * 有测试用例的实证（{@code SearchWildcardApiTest}）。
 *
 * <h2>为什么转义符是 {@code !} 而不是反斜杠</h2>
 *
 * 反斜杠是 {@code LIKE} 的**默认**转义符，用它意味着"转义能不能生效"由数据库的默认值代答 ——
 * 而 MySQL 一旦把 {@code NO_BACKSLASH_ESCAPES} 打开，{@code \%} 就不再是转义序列，
 * 于是**静默失效**：用户输自己完整的登录名反而搜不到（实测 {@code n=0}、不报错）。
 * 本仓在 {@code docs/decisions/0065} 已经吃过一次"由默认值代答"的亏（三个方言的
 * 默认排序规则互不相同），处置也一样：**把这件事写进 SQL**，不依赖任何默认值。
 *
 * <p>所以条件片段显式写成 {@code col LIKE {0} ESCAPE '!'}，于是它对
 * {@code NO_BACKSLASH_ESCAPES} 之类的影响免疫（H2 / MySQL / MySQL+NO_BACKSLASH_ESCAPES
 * 三种组合实测全对）。
 *
 * <h2>怎么用</h2>
 *
 * 两个方法必须**成对**用作 {@code apply} 的两个实参：
 *
 * <pre>{@code
 * String pattern = KeywordLike.pattern(query.getKeyword());
 * ...
 * .and(pattern != null, w -> w
 *         .apply(KeywordLike.condition("username"), pattern)
 *         .or().apply(KeywordLike.condition("display_name"), pattern))
 * }</pre>
 *
 * <p><b>别再直接用 {@code like()}</b>：它会把通配符放出去。这条约定有机械守卫
 * （{@code scripts/verify-like-escaping.mjs}），漏了会红。
 *
 * <p>取舍与实测见 {@code docs/decisions/0067}。
 */
public final class KeywordLike {

    /**
     * 转义符 —— **刻意不用反斜杠**（理由见类注释），也刻意不用 {@code \} 之外的常见候选：
     * 它是 {@code LIKE} 的模式里唯一需要转义的字符，本身用起来最不容易撞。
     */
    public static final char ESCAPE = '!';

    private KeywordLike() {
    }

    /**
     * 关键词 → {@code LIKE} 的实参（已转义，且前后各补一个通配符 {@code %}）。
     *
     * <p>{@code %}、{@code _} 与转义符自身各加一个前导 {@link #ESCAPE}，
     * 于是它们全部退化成字面量 —— 用户输什么就找什么。
     *
     * @param keyword 关键词；{@code null} 或全空白表示"没有这个过滤条件"，返回 {@code null}
     *                （调用方据此决定这条条件要不要拼进 SQL，与各域原有的判空口径一致）
     * @return 形如 {@code %a!_b%} 的实参；无关键词时返回 {@code null}
     */
    public static String pattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        StringBuilder escaped = new StringBuilder(keyword.length() + 8);
        escaped.append('%');
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            if (c == '%' || c == '_' || c == ESCAPE) {
                escaped.append(ESCAPE);
            }
            escaped.append(c);
        }
        escaped.append('%');
        return escaped.toString();
    }

    /**
     * 某一列的 {@code LIKE} 条件片段，与 {@link #pattern(String)} 配对用作 {@code apply} 的两个实参。
     *
     * <p>列名必须是**本仓源码里写死的**物理列名。这里断言它的形状，是为了把
     * "绝不把外部输入拼进 SQL"这条约定变成一处**运行期就会炸**的检查 ——
     * 它拼的是字符串，看着像可以放任何东西进去。
     *
     * @param column 物理列名，须匹配 {@code ^[a-z][a-z0-9_]*$}
     * @return 形如 {@code username LIKE {0} ESCAPE '!'} 的片段（{@code {0}} 由 MyBatis-Plus
     *         格式化成**绑定参数**，不是字符串拼接）
     */
    public static String condition(String column) {
        if (column == null || !column.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException(
                    "列名必须是写死的物理列名（小写字母开头，仅含小写字母/数字/下划线），收到：" + column);
        }
        return column + " LIKE {0} ESCAPE '" + ESCAPE + "'";
    }
}
