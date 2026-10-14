package com.bluewhale.sts.track.clientevent;

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

/**
 * 客户端行为事件接口。
 *
 * <p>所有返回值都套 {@link R}：业务结果只由 {@code code} 表达，
 * HTTP 状态码留给传输层 —— 详见 {@code docs/decisions/0005}。
 *
 * <p><b>本类不含任何业务判断</b>：参数校验交给 Bean Validation，业务规则在
 * {@link ClientEventService}。Controller 只做"把 HTTP 世界翻译成方法调用"这一件事，
 * 这样任何一条业务规则都能脱离 Web 层被测试覆盖。
 *
 * <p><b>上报公开、查询要鉴权 —— 这不是遗漏，是分类：</b>
 * {@code POST} 上报刻意**不加** {@code @RequiresPermission}：登录页出错时也必须能上报。
 * 事件体里自带 {@code userId}，但那是客户端填的、可伪造，**不是归属依据** ——
 * 归属一律取服务端在接收时从会话里拿到的租户。
 * 而 {@code GET} 全部要 {@code event:client:read}：行为事件是"用户做了什么"的记录，
 * 与审计同类，读它需要显式授权，而不是"能上报就能读回"。
 */
@RestController
@RequestMapping("/api/events/client")
@Tag(name = "客户端行为事件", description = "接收前端上报的行为事件、查询与按事件名聚合")
public class ClientEventController {

    private final ClientEventService clientEventService;

    public ClientEventController(ClientEventService clientEventService) {
        this.clientEventService = clientEventService;
    }

    /**
     * 批量上报。**刻意公开**，理由见类注释。
     *
     * <p>令牌若存在仍会被解析并写入上下文 —— 那条路径下租户随之落库；
     * 无令牌（未登录）时租户记为空，且这些记录**查不出来**（见 {@code ClientEventService}）。
     */
    @PostMapping
    @Operation(summary = "批量上报客户端行为事件",
            description = "单批最多 500 条。信封级错误整批拒绝（40000）；条目级错误逐条跳过并在返回值中计数。")
    public R<ClientEventReportResultVO> report(@Valid @RequestBody ClientEventReportRequest request,
                                               HttpServletRequest httpRequest) {
        return R.ok(clientEventService.report(request, resolveIp(httpRequest)));
    }

    @GetMapping
    @RequiresPermission(AuthPermissions.EVENT_CLIENT_READ)
    @Operation(summary = "分页查询客户端行为事件",
            description = "按服务端接收时间倒序。时间窗缺省为最近 7 天。"
                    + "**只返回当前登录租户的记录**；未登录时上报的记录不属于任何租户，因此查不到。")
    public R<PageResult<ClientEventVO>> page(@ParameterObject ClientEventQuery query) {
        return R.ok(clientEventService.page(query));
    }

    @GetMapping("/stats")
    @RequiresPermission(AuthPermissions.EVENT_CLIENT_READ)
    @Operation(summary = "按事件名聚合统计",
            description = "返回时间窗内的总条数与各事件名分布，只包含出现过的名称。同样只统计当前租户。")
    public R<ClientEventStatsVO> stats(@ParameterObject ClientEventStatsQuery query) {
        return R.ok(clientEventService.stats(query));
    }

    @GetMapping("/{id}")
    @RequiresPermission(AuthPermissions.EVENT_CLIENT_READ)
    @Operation(summary = "查询事件详情",
            description = "不存在、或属于其他租户时一律返回 40400 —— 不区分这两种情形，"
                    + "否则错误码本身就泄露了\"这条记录存在\"。")
    public R<ClientEventVO> detail(@PathVariable @Parameter(description = "事件主键") long id) {
        return R.ok(clientEventService.detail(id));
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
