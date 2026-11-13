package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.request.GatewayRouteOverrideRequest;
import com.bluewhale.auth.interfaces.dto.response.GatewayRouteOverrideResponse;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 网关路由覆盖管理 API 契约：管理端维护路由业务属性覆盖（停用/生效窗口）。
 *
 * <p>DB 不存路由定义（权威源为 Nacos，冷备为网关本地 YAML），仅存业务属性覆盖。
 * 网关在 60s 内自动拉取最新快照并过滤路由，管理端写操作无需通知网关。
 * 停用等于该路由全站 404，前端需二次确认。权限码
 * {@code sys:gateway:route-override:list/add/edit/delete}。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/auth/v{version}/gateway/route-overrides", version = "1")
public interface GatewayRouteOverrideApi {

    /**
     * 查询路由覆盖列表（含停用与窗口外条目）
     *
     * @return 路由覆盖条目列表，按路由 ID 升序
     */
    @GetMapping
    Result<List<GatewayRouteOverrideResponse>> list();

    /**
     * 新增路由覆盖
     *
     * @param routeId 路由 ID（与 Nacos/兜底路由 id 逐字一致）
     * @param request 保存请求
     * @return 操作结果
     */
    @PostMapping("/{routeId}")
    Result<Void> create(@PathVariable String routeId, @Valid @RequestBody GatewayRouteOverrideRequest request);

    /**
     * 更新路由覆盖
     *
     * @param routeId 路由 ID
     * @param request 保存请求
     * @return 操作结果
     */
    @PutMapping("/{routeId}")
    Result<Void> update(@PathVariable String routeId, @Valid @RequestBody GatewayRouteOverrideRequest request);

    /**
     * 删除路由覆盖（恢复路由默认行为）
     *
     * @param routeId 路由 ID
     * @return 操作结果
     */
    @DeleteMapping("/{routeId}")
    Result<Void> delete(@PathVariable String routeId);
}
