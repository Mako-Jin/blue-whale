package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.GatewayBlacklistAggregate;

import java.util.List;
import java.util.Optional;

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

    /**
     * 管理端查询黑名单（含已解除项）
     *
     * @param status 状态过滤；{@code null} 表示全部
     * @return 黑名单聚合列表，按 {@code sort_no}、主键升序
     */
    List<GatewayBlacklistAggregate> findAll(Integer status);

    /**
     * 按主键查询
     *
     * @param id 主键
     * @return 黑名单聚合；不存在时返回空
     */
    Optional<GatewayBlacklistAggregate> findById(Long id);

    /**
     * 保存（主键为空时新增，否则更新）
     *
     * @param rule 黑名单聚合
     * @return 保存后的黑名单聚合
     */
    GatewayBlacklistAggregate save(GatewayBlacklistAggregate rule);

    /**
     * 物理删除条目（释放唯一键，便于重新录入同一路径；仅解除封禁请用 {@code status=0}）
     *
     * @param id 主键
     */
    void deleteById(Long id);

    /**
     * 判断同路径 + 方法 + 作用域是否已存在条目
     *
     * @param pathPattern 路径
     * @param httpMethod HTTP 方法
     * @param scope 作用域
     * @param excludeId 需要排除的主键（更新场景），可为 {@code null}
     * @return 已存在返回 {@code true}
     */
    boolean existsRule(String pathPattern, String httpMethod, String scope, Long excludeId);
}
