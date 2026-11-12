package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.domain.aggregate.OAuthClientAggregate;
import com.bluewhale.auth.domain.repository.OAuthClientRepository;
import com.bluewhale.auth.infrastructure.persistence.assembler.OAuthClientPersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysOAuthClientMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysOAuthClientPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * {@link OAuthClientRepository} 的 MyBatis-Plus 实现（权限中心扩展 M0）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class OAuthClientRepositoryImpl implements OAuthClientRepository {

    private final SysOAuthClientMapper oauthClientMapper;

    /**
     * 保存客户端
     *
     * @param client 客户端聚合
     * @return 回填主键后的聚合
     */
    @Override
    public OAuthClientAggregate save(OAuthClientAggregate client) {
        SysOAuthClientPo po = OAuthClientPersistenceAssembler.toPo(client);
        if (po.getId() == null) {
            oauthClientMapper.insert(po);
        } else {
            oauthClientMapper.updateById(po);
        }
        return OAuthClientPersistenceAssembler.toDomain(po);
    }

    /**
     * 按主键查询
     *
     * @param id 主键
     * @return 客户端
     */
    @Override
    public Optional<OAuthClientAggregate> findById(Long id) {
        SysOAuthClientPo po = oauthClientMapper.selectById(id);
        return Optional.ofNullable(po).map(OAuthClientPersistenceAssembler::toDomain);
    }

    /**
     * 按客户端标识查询
     *
     * @param clientId 客户端标识
     * @return 客户端
     */
    @Override
    public Optional<OAuthClientAggregate> findByClientId(String clientId) {
        SysOAuthClientPo po = oauthClientMapper.selectOne(new LambdaQueryWrapper<SysOAuthClientPo>()
                .eq(SysOAuthClientPo::getClientId, clientId)
                .last("limit 1"));
        return Optional.ofNullable(po).map(OAuthClientPersistenceAssembler::toDomain);
    }

    /**
     * 判断客户端标识是否已存在
     *
     * @param clientId 客户端标识
     * @return 已存在返回 {@code true}
     */
    @Override
    public boolean existsByClientId(String clientId) {
        Long count = oauthClientMapper.selectCount(new LambdaQueryWrapper<SysOAuthClientPo>()
                .eq(SysOAuthClientPo::getClientId, clientId));
        return count != null && count > 0;
    }

    /**
     * 分页查询客户端（数据库侧分页）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 关键字
     * @param status 状态
     * @return 客户端分页结果
     */
    @Override
    public OAuthClientPage page(long current, long size, String keyword, Integer status) {
        LambdaQueryWrapper<SysOAuthClientPo> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(SysOAuthClientPo::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            // LIKE 关键字转义反斜杠/百分号/下划线，防止通配符被当作模式展开导致全表扫描
            String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            wrapper.and(w -> w.like(SysOAuthClientPo::getClientId, escaped)
                    .or().like(SysOAuthClientPo::getClientName, escaped));
        }
        wrapper.orderByDesc(SysOAuthClientPo::getCreateTime);
        Page<SysOAuthClientPo> poPage = oauthClientMapper.selectPage(Page.of(current, size), wrapper);
        List<OAuthClientAggregate> records = poPage.getRecords().stream()
                .map(OAuthClientPersistenceAssembler::toDomain)
                .toList();
        return new OAuthClientPage(records, poPage.getTotal());
    }
}
