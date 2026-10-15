package com.bluewhale.sts.common.db.query;

/**
 * 搜索关键词 → {@code LIKE} 条件：**关键词进 SQL 之前的预处理只有一个真值来源** ——
 * 两端空白先吃掉（规范化），{@code %} 与 {@code _} 当字面量（转义），且**转义符写进 SQL**。
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
 * <h2>关键词的规范化由 {@link FilterValue} 提供</h2>
 *
 * 两端的空白先被吃掉：{@code " a "} 与 {@code "a"} 是同一个词。理由不是"顺手清一下"，
 * 而是**这一处的差异不承载任何区分能力** —— {@code LIKE '%…%'} 是包含匹配，
 * 前后空白没法用来表达"我要找以空格开头的那条"，它只能造成"在某些页面上搜不到"。
 * 而这条口径此前散在七个搜索入口上（三个域走私有的 {@code normalize()}、四个域直传），
 * 于是同一条关键词在两个域上结果不同 —— 有测试用例摆着（{@code SearchKeywordTrimApiTest}）。
 * 现在<b>规范化本身</b>收在 {@link FilterValue}（精确匹配的过滤值也用它，见 {@code 0069}），
 * 本类只负责 {@code LIKE} 特有的那部分（转义 + 前后补 {@code %}）。
 * {@code pattern()} 内部调的就是 {@code FilterValue.text()} —— 不做第二套规矩。
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
     * 关键词 → {@code LIKE} 的实参（已**规范化**并转义，前后各补一个通配符 {@code %}）。
     *
     * <p><b>规范化在这里、不在调用方。</b>两端的空白先被吃掉（{@code " a "} 与 {@code "a"} 同义），
     * 吃掉之后仍为空表示"没有这个过滤条件"。这条口径此前**散在七个搜索入口上**
     * （组织 / 角色 / 用户组各自私有 {@code normalize()}、用户 / 租户 / 资源 / 客户端日志直传），
     * 于是同一个词在组织域搜得到、在用户域搜不到 —— 有测试用例的实证
     * （{@code SearchKeywordTrimApiTest} 摆着两个域的同一条断言）。收在 {@link FilterValue}
     * 之后，"过滤值长什么样"只剩这一个真值来源，调用方一律写 {@code pattern(query.getKeyword())}。
     *
     * <p>{@code %}、{@code _} 与转义符自身各加一个前导 {@link #ESCAPE}，
     * 于是它们全部退化成字面量 —— 用户输什么就找什么。
     *
     * @param keyword 关键词；{@code null} 或**只含空白**表示"没有这个过滤条件"，
     *                返回 {@code null}（调用方据此决定这条条件要不要拼进 SQL）
     * @return 形如 {@code %a!_b%} 的实参；无关键词时返回 {@code null}
     */
    public static String pattern(String keyword) {
        // 规范化（只吃两端、不吃中间）下沉到 FilterValue：精确匹配的过滤值走的是同一个方法，
        // 于是"空白怎么处理"在一处定义，两类值不会各写一套。
        String normalized = FilterValue.text(keyword);
        if (normalized == null) {
            return null;
        }
        StringBuilder escaped = new StringBuilder(normalized.length() + 8);
        escaped.append('%');
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
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
