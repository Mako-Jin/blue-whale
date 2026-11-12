package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.domain.aggregate.GatewayBlacklistAggregate;
import com.bluewhale.auth.domain.repository.GatewayBlacklistRepository;
import com.bluewhale.auth.infrastructure.persistence.assembler.GatewayBlacklistPersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysGatewayBlacklistMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysGatewayBlacklistPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link GatewayBlacklistRepository} 的 MyBatis-Plus 实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class GatewayBlacklistRepositoryImpl implements GatewayBlacklistRepository {

    private final SysGatewayBlacklistMapper gatewayBlacklistMapper;

    /**
     * 查询全部生效的平台级黑名单
     *
     * @return 生效的黑名单聚合列表
     */
    @Override
    public List<GatewayBlacklistAggregate> findAllEnabled() {
        List<SysGatewayBlacklistPo> records = gatewayBlacklistMapper.selectList(
                new LambdaQueryWrapper<SysGatewayBlacklistPo>()
                        .eq(SysGatewayBlacklistPo::getStatus, GatewayBlacklistAggregate.STATUS_ENABLED)
                        .eq(SysGatewayBlacklistPo::getScope, GatewayBlacklistAggregate.SCOPE_PLATFORM)
                        .orderByAsc(SysGatewayBlacklistPo::getSortNo)
                        .orderByAsc(SysGatewayBlacklistPo::getId));
        return records.stream().map(GatewayBlacklistPersistenceAssembler::toDomain).toList();
    }

    /**
     * 管理端查询黑名单（含已解除项）
     *
     * @param status 状态过滤；{@code null} 表示全部
     * @return 黑名单聚合列表
     */
    @Override
    public List<GatewayBlacklistAggregate> findAll(Integer status) {
        List<SysGatewayBlacklistPo> records = gatewayBlacklistMapper.selectList(
                new LambdaQueryWrapper<SysGatewayBlacklistPo>()
                        .eq(status != null, SysGatewayBlacklistPo::getStatus, status)
                        .eq(SysGatewayBlacklistPo::getScope, GatewayBlacklistAggregate.SCOPE_PLATFORM)
                        .orderByAsc(SysGatewayBlacklistPo::getSortNo)
                        .orderByAsc(SysGatewayBlacklistPo::getId));
        return records.stream().map(GatewayBlacklistPersistenceAssembler::toDomain).toList();
    }

    /**
     * 按主键查询
     *
     * @param id 主键
     * @return 黑名单聚合；不存在时返回空
     */
    @Override
    public Optional<GatewayBlacklistAggregate> findById(Long id) {
        return Optional.ofNullable(gatewayBlacklistMapper.selectById(id))
                .map(GatewayBlacklistPersistenceAssembler::toDomain);
    }

    /**
     * 保存（主键为空时新增，否则更新）
     *
     * @param rule 黑名单聚合
     * @return 保存后的黑名单聚合
     */
    @Override
    public GatewayBlacklistAggregate save(GatewayBlacklistAggregate rule) {
        SysGatewayBlacklistPo po = GatewayBlacklistPersistenceAssembler.toPo(rule);
        if (po.getId() == null) {
            gatewayBlacklistMapper.insert(po);
        } else {
            gatewayBlacklistMapper.updateById(po);
        }
        return GatewayBlacklistPersistenceAssembler.toDomain(po);
    }

    /**
     * 物理删除条目
     *
     * @param id 主键
     */
    @Override
    public void deleteById(Long id) {
        gatewayBlacklistMapper.deletePhysically(id);
    }

    /**
     * 判断同路径 + 方法 + 作用域是否已存在条目
     *
     * @param pathPattern 路径
     * @param httpMethod HTTP 方法
     * @param scope 作用域
     * @param excludeId 需要排除的主键（更新场景），可为 {@code null}
     * @return 已存在返回 {@code true}
     */
    @Override
    public boolean existsRule(String pathPattern, String httpMethod, String scope, Long excludeId) {
        Long count = gatewayBlacklistMapper.selectCount(
                new LambdaQueryWrapper<SysGatewayBlacklistPo>()
                        .eq(SysGatewayBlacklistPo::getPathPattern, pathPattern)
                        .eq(SysGatewayBlacklistPo::getHttpMethod, httpMethod)
                        .eq(SysGatewayBlacklistPo::getScope, scope)
                        .ne(excludeId != null, SysGatewayBlacklistPo::getId, excludeId));
        return count != null && count > 0;
    }
}
