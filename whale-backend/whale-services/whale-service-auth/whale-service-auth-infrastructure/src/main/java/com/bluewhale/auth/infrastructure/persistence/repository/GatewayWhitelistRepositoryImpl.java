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
}
