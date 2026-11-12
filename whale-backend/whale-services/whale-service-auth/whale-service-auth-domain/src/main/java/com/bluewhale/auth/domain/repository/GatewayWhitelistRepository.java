package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.GatewayWhitelistAggregate;

import java.util.List;

/**
 * 网关白名单聚合仓储端口：契约定义于领域层，由 {@code infrastructure} 适配实现。
 *
 * <p>{@code sys_gateway_whitelist} 为平台级全局表，网关拉取不携带租户上下文，
 * 本仓储不参与租户行级过滤。</p>
 *
 * @author BlueWhale
 */
public interface GatewayWhitelistRepository {

    /**
     * 查询全部启用的平台级白名单，按 {@code sort_no}、主键升序
     *
     * @return 启用的白名单聚合列表；无数据时返回空列表
     */
    List<GatewayWhitelistAggregate> findAllEnabled();
}
