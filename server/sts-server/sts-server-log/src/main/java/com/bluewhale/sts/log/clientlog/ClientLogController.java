package com.bluewhale.sts.log.clientlog;

import com.bluewhale.sts.auth.authz.RequiresPermission;
import com.bluewhale.sts.auth.common.AuthPermissions;
import com.bluewhale.sts.common.basic.model.PageResult;
import com.bluewhale.sts.common.basic.model.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户端日志接口。
 *
 * <p>所有返回值都套 {@link R}：业务结果只由 {@code code} 表达，
 * HTTP 状态码留给传输层 —— 详见 {@code docs/decisions/0005}。
 *
 * <p><b>本类不含任何业务判断</b>：参数校验交给 Bean Validation，业务规则在
 * {@link ClientLogService}。Controller 只做"把 HTTP 世界翻译成方法调用"这一件事，
 * 这样任何一条业务规则都能脱离 Web 层被测试覆盖。
 *
 * <p><b>上报公开、查询要鉴权 —— 这不是遗漏，是分类：</b>
 * {@code POST} 上报刻意**不加** {@code @RequiresPermission}：登录页出错时也必须能上报，
 * 否则最需要诊断的那批日志恰好发不出来（未登录 → 无令牌 → 被拒）。
 * 代价是上报内容整体不可信 —— 包括它自带的"用户是谁"。
 * 而 {@code GET} 全部要 {@code log:client:read}：日志正文里会有报错信息、上下文对象与 IP，
 * 读它等于读别人系统的内部状态，"能上报"绝不蕴含"能读回"。
 * 修复前的状态是这两者一起公开，任何能访问服务的人都能读到全部租户的日志。
 */
@RestController
@RequestMapping("/api/logs/client")
@Tag(name = "客户端日志", description = "接收前端上报的技术日志、查询与聚合；审计日志不走本接口")
public class ClientLogController {

    private final ClientLogService clientLogService;

    public ClientLogController(ClientLogService clientLogService) {
        this.clientLogService = clientLogService;
    }

    /**
     * 批量上报。**刻意公开**，理由见类注释。
     *
     * <p>令牌若存在仍会被解析并写入上下文 —— 那条路径下租户随之落库；
     * 无令牌（未登录）时租户记为空，且这些记录**查不出来**（见 {@code ClientLogService}）。
     */
    @PostMapping
    @Operation(summary = "批量上报客户端日志",
            description = "单批最多 500 条。信封级错误整批拒绝（40000）；条目级错误逐条跳过并在返回值中计数。")
    public R<ClientLogReportResultVO> report(@Valid @RequestBody ClientLogReportRequest request,
                                             HttpServletRequest httpRequest) {
        return R.ok(clientLogService.report(request, resolveIp(httpRequest)));
    }

    @GetMapping
    @RequiresPermission(AuthPermissions.LOG_CLIENT_READ)
    @Operation(summary = "分页查询客户端日志",
            description = "按服务端接收时间倒序。时间窗缺省为最近 7 天。"
                    + "**只返回当前登录租户的记录**；未登录时上报的记录不属于任何租户，因此查不到。")
    public R<PageResult<ClientLogVO>> page(@ParameterObject ClientLogQuery query) {
        return R.ok(clientLogService.page(query));
    }

    @GetMapping("/stats")
    @RequiresPermission(AuthPermissions.LOG_CLIENT_READ)
    @Operation(summary = "按级别聚合统计",
            description = "返回时间窗内的总条数与各级别分布，包含条数为 0 的级别。同样只统计当前租户。")
    public R<ClientLogStatsVO> stats(@ParameterObject ClientLogStatsQuery query) {
        return R.ok(clientLogService.stats(query));
    }

    @GetMapping("/trace/{traceId}")
    @RequiresPermission(AuthPermissions.LOG_CLIENT_READ)
    @Operation(summary = "按链路标识查询整条链路",
            description = "按发生时间**升序**返回，便于直接按顺序阅读。"
                    + "最多返回 200 条；时间窗缺省为最近 7 天，与分页查询、聚合统计一致。")
    public R<List<ClientLogVO>> listByTraceId(@PathVariable @Parameter(description = "链路标识") String traceId) {
        return R.ok(clientLogService.listByTraceId(traceId));
    }

    @GetMapping("/{id}")
    @RequiresPermission(AuthPermissions.LOG_CLIENT_READ)
    @Operation(summary = "查询日志详情",
            description = "不存在、或属于其他租户时一律返回 40400 —— 不区分这两种情形，"
                    + "否则错误码本身就泄露了\"这条记录存在\"。")
    public R<ClientLogVO> detail(@PathVariable @Parameter(description = "日志主键") long id) {
        return R.ok(clientLogService.detail(id));
    }

    /**
     * 解析上报方 IP。
     *
     * <p>优先取 {@code X-Forwarded-For} 的**第一段**：经过代理时 {@code getRemoteAddr()}
     * 拿到的是最后一跳代理的地址，记下来毫无排查价值。取第一段而非最后一段，
     * 是因为最靠近客户端的那个才是真实来源。
     *
     * <p>需注意该头可被伪造，因此它只用于排查参考，**不可作为鉴权或风控依据**。
     */
    private static String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

}
