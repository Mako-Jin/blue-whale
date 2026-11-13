package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.domain.aggregate.GatewayRouteOverrideAggregate;
import com.bluewhale.auth.domain.repository.GatewayRouteOverrideRepository;
import com.bluewhale.auth.infrastructure.persistence.assembler.GatewayRouteOverridePersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysGatewayRouteOverrideMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysGatewayRouteOverridePo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link GatewayRouteOverrideRepository} 的 MyBatis-Plus 实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class GatewayRouteOverrideRepositoryImpl implements GatewayRouteOverrideRepository {

    private final SysGatewayRouteOverrideMapper routeOverrideMapper;

    /**
     * 查询全部覆盖条目（含停用与窗口外条目）
     *
     * @return 覆盖聚合列表，按路由 ID 升序
     */
    @Override
    public List<GatewayRouteOverrideAggregate> findAll() {
        List<SysGatewayRouteOverridePo> records = routeOverrideMapper.selectList(
                new LambdaQueryWrapper<SysGatewayRouteOverridePo>()
                        .orderByAsc(SysGatewayRouteOverridePo::getRouteId));
        return records.stream().map(GatewayRouteOverridePersistenceAssembler::toDomain).toList();
    }

    /**
     * 按路由 ID 查询
     *
     * @param routeId 路由 ID
     * @return 覆盖聚合；不存在时返回空
     */
    @Override
    public Optional<GatewayRouteOverrideAggregate> findById(String routeId) {
        return Optional.ofNullable(routeOverrideMapper.selectById(routeId))
                .map(GatewayRouteOverridePersistenceAssembler::toDomain);
    }

    /**
     * 保存（主键为路由 ID：不存在时新增，存在时更新）
     *
     * @param override 覆盖聚合
     * @return 保存后的覆盖聚合
     */
    @Override
    public GatewayRouteOverrideAggregate save(GatewayRouteOverrideAggregate override) {
        SysGatewayRouteOverridePo po = GatewayRouteOverridePersistenceAssembler.toPo(override);
        if (routeOverrideMapper.selectById(po.getRouteId()) == null) {
            routeOverrideMapper.insert(po);
        } else {
            routeOverrideMapper.updateById(po);
        }
        return GatewayRouteOverridePersistenceAssembler.toDomain(po);
    }

    /**
     * 物理删除条目
     *
     * @param routeId 路由 ID
     */
    @Override
    public void deleteById(String routeId) {
        routeOverrideMapper.deleteById(routeId);
    }
}
