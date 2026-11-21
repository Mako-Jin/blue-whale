package com.bluewhale.auth.application.port;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 枢密殿（权限认证）安全态势统计端口：三维王城总控 T5.2 的数据来源。
 *
 * <p><b>为什么单独开端口而不是复用列表接口。</b>列表接口回答的是"有哪些行"，
 * 总控回答的是"现在什么样"：账号是不是还在启用、会话是不是还活着、今天有没有人在撞库。
 * 拿分页接口的 total 去当指标，等于把"这一页查得动"当成"系统健康"——
 * 分页 total 在带筛选条件时会变，而指标不该跟着筛选走。</p>
 *
 * <p><b>租户必须显式传参，不依赖线程上下文。</b>本服务装了行级租户插件，但它的语义是
 * "有上下文才拼条件"：总控走服务间调用，链路不保证每段都带租户头，一旦上下文为空，
 * 插件直接放行，统计就退化成全库聚合——把别家租户的账号数报给这个租户，
 * 既不报错也查不出，只是数字大一点。所以这里把 {@code tenantId} 当参数传下来；
 * 为 {@code null} 时返回 0，宁可显示"没有数"，也不要显示"别人的数"。</p>
 *
 * <p>时间窗口由调用方给定（今日 = {@code [今天 00:00, 明天 00:00)}），
 * 不在 SQL 里写 {@code CURDATE()}：应用与数据库时区不一致时，
 * "今天的登录"会变成"昨天下午到今天晚上"，这类偏移在值班时最容易被误读成业务异常。</p>
 *
 * @author BlueWhale
 */
public interface AuthSecurityStatQuery {

    /**
     * 统计启用账号数
     *
     * <p>口径：未逻辑删除且 {@code status = 1}。停用账号不参与计数——
     * 总控要回答的是"现在有多少人能登录"，把停用账号算进去会让这个数虚高。</p>
     *
     * @param tenantId 租户 ID，{@code null} 时返回 0
     * @return 启用账号数
     */
    long countEnabledUsers(Long tenantId);

    /**
     * 统计在用会话数
     *
     * <p>口径：{@code sys_refresh_token} 中未撤销且未过期的记录。
     * 这是"会话账本"，不是 Redis 实时计数：账本可能比真实在线数略多（客户端关页未登出），
     * 但它能按租户稳定聚合，且不会因为 Redis 重启而瞬间归零。</p>
     *
     * @param tenantId 租户 ID，{@code null} 时返回 0
     * @param now 当前时刻（由调用方给，便于测试与口径统一）
     * @return 在用会话数
     */
    long countActiveSessions(Long tenantId, LocalDateTime now);

    /**
     * 按天、按登录结果统计登录次数
     *
     * <p>一次查询同时供"今日成功/失败"与"近 7 日趋势"两张图使用：
     * 分两次查会出现两者对不上的情况（跨零点时尤其明显）。</p>
     *
     * @param tenantId 租户 ID，{@code null} 时返回空列表
     * @param fromDay 起始日（含）
     * @param toDay 截止日（不含）
     * @return 每天每种结果一条；无数据时返回空列表
     */
    List<LoginDayCount> countLoginByDay(Long tenantId, LocalDate fromDay, LocalDate toDay);

    /**
     * 单日单结果的登录次数
     *
     * @param day 日期
     * @param loginResult 结果码：SUCCESS / FAILURE / LOGOUT
     * @param total 次数
     * @author BlueWhale
     */
    record LoginDayCount(LocalDate day, String loginResult, long total) {
    }
}
