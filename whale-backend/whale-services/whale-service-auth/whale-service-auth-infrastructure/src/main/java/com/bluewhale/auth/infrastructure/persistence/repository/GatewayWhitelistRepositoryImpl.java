package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.domain.aggregate.GatewayWhitelistAggregate;
import com.bluewhale.auth.domain.repository.GatewayWhitelistRepository;
import com.bluewhale.auth.infrastructure.persistence.assembler.GatewayWhitelistPersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysGatewayWhitelistMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysGatewayWhitelistPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link GatewayWhitelistRepository} 的 MyBatis-Plus 实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class GatewayWhitelistRepositoryImpl implements GatewayWhitelistRepository {

    private final SysGatewayWhitelistMapper gatewayWhitelistMapper;

    /**
     * 查询全部启用的平台级白名单
     *
     * @return 启用的白名单聚合列表
     */
    @Override
    public List<GatewayWhitelistAggregate> findAllEnabled() {
        List<SysGatewayWhitelistPo> records = gatewayWhitelistMapper.selectList(
                new LambdaQueryWrapper<SysGatewayWhitelistPo>()
                        .eq(SysGatewayWhitelistPo::getStatus, GatewayWhitelistAggregate.STATUS_ENABLED)
                        .eq(SysGatewayWhitelistPo::getScope, GatewayWhitelistAggregate.SCOPE_PLATFORM)
                        .orderByAsc(SysGatewayWhitelistPo::getSortNo)
                        .orderByAsc(SysGatewayWhitelistPo::getId));
        return records.stream().map(GatewayWhitelistPersistenceAssembler::toDomain).toList();
    }

    /**
     * 管理端查询白名单（含停用项）
     *
     * @param status 状态过滤；{@code null} 表示全部
     * @return 白名单聚合列表
     */
    @Override
    public List<GatewayWhitelistAggregate> findAll(Integer status) {
        List<SysGatewayWhitelistPo> records = gatewayWhitelistMapper.selectList(
                new LambdaQueryWrapper<SysGatewayWhitelistPo>()
                        .eq(status != null, SysGatewayWhitelistPo::getStatus, status)
                        .eq(SysGatewayWhitelistPo::getScope, GatewayWhitelistAggregate.SCOPE_PLATFORM)
                        .orderByAsc(SysGatewayWhitelistPo::getSortNo)
                        .orderByAsc(SysGatewayWhitelistPo::getId));
        return records.stream().map(GatewayWhitelistPersistenceAssembler::toDomain).toList();
    }

    /**
     * 按主键查询
     *
     * @param id 主键
     * @return 白名单聚合；不存在时返回空
     */
    @Override
    public Optional<GatewayWhitelistAggregate> findById(Long id) {
        return Optional.ofNullable(gatewayWhitelistMapper.selectById(id))
                .map(GatewayWhitelistPersistenceAssembler::toDomain);
    }

    /**
     * 保存（主键为空时新增，否则更新）
     *
     * @param rule 白名单聚合
     * @return 保存后的白名单聚合
     */
    @Override
    public GatewayWhitelistAggregate save(GatewayWhitelistAggregate rule) {
        SysGatewayWhitelistPo po = GatewayWhitelistPersistenceAssembler.toPo(rule);
        if (po.getId() == null) {
            gatewayWhitelistMapper.insert(po);
        } else {
            gatewayWhitelistMapper.updateById(po);
        }
        return GatewayWhitelistPersistenceAssembler.toDomain(po);
    }

    /**
     * 物理删除条目
     *
     * @param id 主键
     */
    @Override
    public void deleteById(Long id) {
        gatewayWhitelistMapper.deletePhysically(id);
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
        Long count = gatewayWhitelistMapper.selectCount(
                new LambdaQueryWrapper<SysGatewayWhitelistPo>()
                        .eq(SysGatewayWhitelistPo::getPathPattern, pathPattern)
                        .eq(SysGatewayWhitelistPo::getHttpMethod, httpMethod)
                        .eq(SysGatewayWhitelistPo::getScope, scope)
                        .ne(excludeId != null, SysGatewayWhitelistPo::getId, excludeId));
        return count != null && count > 0;
    }
}
