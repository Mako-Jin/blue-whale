package com.bluewhale.sts.common.db.query;

import java.util.function.Function;

/**
 * 过滤值 → 进 SQL 之前的**规范化**：吃掉两端空白，吃掉之后仍为空表示"没有这个过滤条件"。
 *
 * <h2>为什么需要它</h2>
 *
 * 过滤值（{@code status} / {@code app} / {@code traceId} …）是<b>精确匹配</b>（{@code eq}）的，
 * 而各域写库前都过了一遍规范化，所以库里的值<b>不会</b>带两端空白。
 * 于是 {@code ?status=%20ENABLED%20} 不是一个"想精确匹配带空格的"的意图 ——
 * 它<b>永远</b>匹配不到任何行。这一处的差异不承载任何区分能力，只承载"在某些页面上搜不到"。
 *
 * <p>这条口径此前散在四处：组织 / 角色 / 用户组走各自私有的 {@code normalize()}（会 trim）、
 * 用户 / 租户 / 日志 / 事件 / 审计把原值直接交给 {@code eq}（只判空、不 trim）。
 * 于是同一个 {@code status} 参数在组织域搜得到、在用户域搜不到 —— 有测试用例摆着
 * （{@code FilterValueTrimApiTest} 里两个域的同形断言）。
 *
 * <h2>它还会顺手消除一处内部不一致</h2>
 *
 * 日志 / 事件 / 审计三域的聚合统计走**手写 SQL**，那里的判空写的是 Ognl 的
 * {@code <if test="app != null and app != ''">}，而同一个请求的列表走的是 Java 的
 * {@code hasText()}（{@code isBlank}）—— 两者对 {@code "   "} 的判定<b>相反</b>。
 * 两边都改用本方法的结果之后，判定自然同义（{@code FilterValueTrimApiTest} 的
 * {@code shouldKeepStatisticsInSyncWithPage} 钉这一条）。
 *
 * <h2>怎么用</h2>
 *
 * <p><b>先取局部变量，再拿它当条件的开关</b> —— 因为
 * {@code eq(条件, 列, 值)} 的"值"是**无条件求值**的，写成
 * {@code query.getApp().trim()} 会在值为 {@code null} 时当场 NPE。
 * 这条坑在本仓已经踩过多次，形态如下：
 *
 * <pre>{@code
 * String app = FilterValue.text(query.getApp());
 * ...
 * return Wrappers.<ClientLogEntity>lambdaQuery()
 *         .eq(app != null, ClientLogEntity::getApp, app);
 * }</pre>
 *
 * <p>手写 SQL 的聚合路径要用**同一个局部变量**（别各算一遍）：
 * {@code countByLevel(tenantId, app, startTime, endTime)}。
 *
 * <p>与 {@link KeywordLike} 的分工：关键词走 {@code LIKE}（还要转义通配符、补 {@code %}），
 * 精确匹配的过滤值走这里。{@code KeywordLike.pattern()} 内部用的也是本方法 ——
 * "两端空白怎么处理"只有一个实现。
 *
 * <h2>两类过滤值，两个入口</h2>
 *
 * <p>{@link #text(String)} 管**自由文本**（{@code app} / {@code traceId} / 审计的 {@code action}），
 * {@link #enumValue} 管**封闭取值集**（列里存的是枚举的 {@code wireName}）。
 * 分界线是"这一列有没有一个可以用完的取值表"：有，换个大小写就不是另一个对象，
 * 归一它是**修正写法**；没有，换个大小写就是另一个字符串，归一它是**篡改意图**。
 * 自由文本因此沿用 {@code 0065} 定下的"区分大小写"，不在这里动。
 *
 * <p>取舍与实测见 {@code docs/decisions/0069}（空白）与 {@code docs/decisions/0070}（枚举）。
 */
public final class FilterValue {

    private FilterValue() {
    }

    /**
     * 规范化一个精确匹配的过滤值：吃掉两端空白；空串与纯空白退化为 {@code null}（= 不过滤）。
     *
     * <p>只吃两端、不吃中间 —— 但这一条对精确匹配的值来说几乎没有机会表现：
     * 过滤值都是编码 / 枚举 / 标识（{@code ENABLED}、{@code it-page-xxx}、{@code trace-…}），
     * 内部本来就不带空格。与关键词不同（那里 {@code "a b"} 是合法输入），
     * 这里保留"不吃中间"主要是与 {@link KeywordLike} 保持同一份语义，不做第二套规矩。
     *
     * @param value 原始过滤值；{@code null} 或**只含空白**表示"没有这个过滤条件"
     * @return 去掉两端空白后的值；无实质内容时返回 {@code null}
     */
    public static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 规范化一个**枚举型**过滤值：先 {@link #text(String)}，再让对方把自己的
     * {@code parse} 认一认 —— 认得就换成标准写法（{@code enabled} → {@code ENABLED}），
     * 认不得就<b>按原样比</b>。
     *
     * <h2>为什么认不得时按原样比，而不是退化成"不过滤"</h2>
     *
     * <p>{@code ?status=EANBLED}（拼错）如果变成"不过滤"，调用方会拿到<b>全部</b>记录，
     * 却以为自己筛过了 —— 这比报错危险得多，也和"结果为空"差着一整个数量级。
     * 原样比的结果是空集：既没有假装成功，也没有把查询拦下来（{@code FilterValueTrimApiTest}
     * 与 {@code EnumFilterNormalizationApiTest} 各钉一条）。
     *
     * <h2>怎么用</h2>
     *
     * <pre>{@code
     * String status = FilterValue.enumValue(query.getStatus(), Status::parse, Status::wireName);
     * ...
     * .eq(status != null, RoleEntity::getStatus, status);
     * }</pre>
     *
     * <p>两个方法引用分别回答"这是什么"和"它怎么落库"。**不要让枚举类实现一个公共接口来省掉参数** ——
     * 那会把 {@code sts-common-db} 变成各域枚举的共同依赖方向，而这里需要的只是"调用方告诉我怎么解析"。
     *
     * @param raw    原始过滤值；{@code null} 或只含空白表示"没有这个过滤条件"
     * @param parser 该枚举的解析方法（大小写不敏感、认不得回 {@code null}）
     * @param wire   该枚举落库用的写法，即 {@code wireName()}
     * @return 归一后的标准写法；认不得的值原样返回（已被 trim）；无实质内容时返回 {@code null}
     */
    public static <E extends Enum<E>> String enumValue(String raw,
                                                       Function<String, E> parser,
                                                       Function<E, String> wire) {
        String normalized = text(raw);
        if (normalized == null) {
            return null;
        }
        E parsed = parser.apply(normalized);
        return parsed == null ? normalized : wire.apply(parsed);
    }
}
