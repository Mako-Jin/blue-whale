package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.GatewayRouteOverrideAggregate;

import java.util.List;
import java.util.Optional;

/**
 * 网关路由业务属性覆盖聚合仓储端口：契约定义于领域层，由 {@code infrastructure} 适配实现。
 *
 * <p>{@code sys_gateway_route_override} 为平台级全局表（主键即路由 id），网关拉取不携带
 * 租户上下文，本仓储不参与租户行级过滤。</p>
 *
 * @author BlueWhale
 */
public interface GatewayRouteOverrideRepository {

    /**
     * 查询全部覆盖条目（含停用与窗口外条目，网关按当前时间自行判定生效）
     *
     * @return 覆盖聚合列表，按路由 ID 升序；无数据时返回空列表
     */
    List<GatewayRouteOverrideAggregate> findAll();

    /**
     * 按路由 ID 查询
     *
     * @param routeId 路由 ID
     * @return 覆盖聚合；不存在时返回空
     */
    Optional<GatewayRouteOverrideAggregate> findById(String routeId);

    /**
     * 保存（新增或更新，主键为路由 ID）
     *
     * @param override 覆盖聚合
     * @return 保存后的覆盖聚合
     */
    GatewayRouteOverrideAggregate save(GatewayRouteOverrideAggregate override);

    /**
     * 物理删除条目（按路由 ID）
     *
     * @param routeId 路由 ID
     */
    void deleteById(String routeId);
}
