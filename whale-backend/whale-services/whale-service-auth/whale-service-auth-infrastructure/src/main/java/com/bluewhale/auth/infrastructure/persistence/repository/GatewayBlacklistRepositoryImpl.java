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
}
