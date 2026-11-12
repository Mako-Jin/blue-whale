package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.GatewayBlacklistAggregate;

import java.util.List;

/**
 * 网关黑名单聚合仓储端口：契约定义于领域层，由 {@code infrastructure} 适配实现。
 *
 * <p>{@code sys_gateway_blacklist} 为平台级全局表，网关拉取不携带租户上下文，
 * 本仓储不参与租户行级过滤。</p>
 *
 * @author BlueWhale
 */
public interface GatewayBlacklistRepository {

    /**
     * 查询全部生效的平台级黑名单，按 {@code sort_no}、主键升序
     *
     * @return 生效的黑名单聚合列表；无数据时返回空列表
     */
    List<GatewayBlacklistAggregate> findAllEnabled();
}
