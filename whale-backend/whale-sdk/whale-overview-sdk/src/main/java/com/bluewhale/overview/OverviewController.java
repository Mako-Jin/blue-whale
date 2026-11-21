package com.bluewhale.overview;

import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.overview.model.OverviewSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 概览端点：{@code GET ${whale.overview.base-path}}。
 *
 * <p>路径取自配置（示例 {@code /erp/overview}），因为服务一级前缀本来就不在 Java 代码里
 * ——REQ-ARCH-003 规定网关不剥前缀。</p>
 *
 * <p>权限判定放在控制器而不是 provider：provider 在独立线程池里跑、拿不到请求头；
 * 而"这个用户能不能看这个概览"必须在请求线程上一次性判定完，
 * 否则 provider 会被无权限的调用反复触发（等于给对方一个免费的慢查询入口）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("${whale.overview.base-path:/overview}")
@RequiredArgsConstructor
public class OverviewController {

    /** 概览装载服务 */
    private final OverviewService overviewService;

    /**
     * 查询概览快照
     *
     * <p>永不返回 500：provider 失败会降级成 {@code stale} / {@code degraded} 快照，
     * 让 3D 厅能画出"闭门"而不是白屏。</p>
     *
     * @param node 节点 key；服务只有一个节点时可省略
     * @param request 当前请求（取身份与权限码头）
     * @return 概览快照
     */
    @GetMapping
    public Result<OverviewSnapshot> overview(
            @RequestParam(value = "node", required = false) String node,
            HttpServletRequest request) {
        OverviewProvider provider = overviewService.resolve(node);
        requirePermission(request, provider);
        return Result.ok(overviewService.load(contextOf(request), provider));
    }

    /**
     * 列出本服务全部概览节点
     *
     * <p>前端不需要它（节点是配置的），但运维排查"某个殿为什么没数"时，
     * 第一句要问的就是"这个服务到底暴露了哪些节点"。</p>
     *
     * @return 节点清单
     */
    @GetMapping("/nodes")
    public Result<List<OverviewNodeVO>> nodes() {
        return Result.ok(overviewService.providers().stream()
                .map(provider -> new OverviewNodeVO(provider.service(), provider.nodeKey(), provider.title(),
                        provider.permissionCode()))
                .toList());
    }

    /**
     * 解析调用者上下文（先快照再进线程池，异步化后不会失效）
     *
     * <p>角色码与权限码一起快照：权限回答"能不能看"，角色回答"看多大范围"。
     * 两者都取自网关注入的可信身份头，provider 侧不再回读请求。</p>
     *
     * @param request 当前请求
     * @return 上下文
     */
    private OverviewContext contextOf(HttpServletRequest request) {
        Long userId = UserContext.findUserId(request);
        Long tenantId = RequestContextHolder.getTenantId();
        Set<String> codes = UserContext.permissionCodes(request);
        Set<String> roles = WebHeaders.splitCodes(WebHeaders.header(request, WebHeaders.USER_ROLES));
        return OverviewContext.of(userId, tenantId, codes, roles);
    }

    /**
     * 校验节点权限码
     *
     * @param request 当前请求
     * @param provider 目标 provider
     * @throws BizException 缺权限码时抛 403
     */
    private void requirePermission(HttpServletRequest request, OverviewProvider provider) {
        String required = provider.permissionCode();
        if (required != null && !required.isBlank() && !UserContext.hasPermission(request, required)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
