package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.domain.aggregate.SocialBindingAggregate;
import com.bluewhale.auth.domain.model.vo.SocialProvider;
import com.bluewhale.auth.domain.repository.SocialBindingRepository;
import com.bluewhale.auth.infrastructure.persistence.assembler.SocialBindingPersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserSocialBindingMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserSocialBindingPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 三方绑定仓储 MyBatis-Plus 实现（权限中心扩展 M2）。
 *
 * <p>逻辑删除由 {@code deleted} 列承担（MyBatis-Plus 全局配置）；解绑走
 * {@code status=0} 更新，记录保留供审计。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SocialBindingRepositoryImpl implements SocialBindingRepository {

    private final SysUserSocialBindingMapper bindingMapper;

    /**
     * 保存绑定聚合：无主键新增（回填 ID），有主键按 ID 更新
     *
     * @param binding 绑定聚合
     * @return 回填主键后的聚合
     */
    @Override
    public SocialBindingAggregate save(SocialBindingAggregate binding) {
        SysUserSocialBindingPo po = SocialBindingPersistenceAssembler.toPo(binding);
        if (po.getId() == null) {
            bindingMapper.insert(po);
        } else {
            bindingMapper.updateById(po);
        }
        return SocialBindingPersistenceAssembler.toDomain(po);
    }

    /**
     * 按主键查询
     *
     * @param id 主键
     * @return 绑定聚合；不存在时返回空
     */
    @Override
    public Optional<SocialBindingAggregate> findById(Long id) {
        SysUserSocialBindingPo po = bindingMapper.selectById(id);
        return Optional.ofNullable(po).map(SocialBindingPersistenceAssembler::toDomain);
    }

    /**
     * 按三方身份查询绑定（唯一键兜底取最新一条）
     *
     * @param provider 三方渠道
     * @param providerUserId 三方用户标识
     * @return 绑定聚合；不存在时返回空
     */
    @Override
    public Optional<SocialBindingAggregate> findByProviderUser(SocialProvider provider, String providerUserId) {
        List<SysUserSocialBindingPo> list = bindingMapper.selectList(new LambdaQueryWrapper<SysUserSocialBindingPo>()
                .eq(SysUserSocialBindingPo::getProvider, provider.code())
                .eq(SysUserSocialBindingPo::getProviderUserId, providerUserId));
        return list.stream()
                .max(Comparator.comparing(SysUserSocialBindingPo::getCreateTime))
                .map(SocialBindingPersistenceAssembler::toDomain);
    }

    /**
     * 查询用户在某渠道下的有效绑定
     *
     * @param userId 用户 ID
     * @param provider 三方渠道
     * @return 绑定聚合；不存在或已解绑时返回空
     */
    @Override
    public Optional<SocialBindingAggregate> findBoundByUserAndProvider(Long userId, SocialProvider provider) {
        SysUserSocialBindingPo po = bindingMapper.selectOne(new LambdaQueryWrapper<SysUserSocialBindingPo>()
                .eq(SysUserSocialBindingPo::getUserId, userId)
                .eq(SysUserSocialBindingPo::getProvider, provider.code())
                .eq(SysUserSocialBindingPo::getStatus, SocialBindingAggregate.STATUS_BOUND)
                .last("limit 1"));
        return Optional.ofNullable(po).map(SocialBindingPersistenceAssembler::toDomain);
    }

    /**
     * 查询用户的全部有效绑定（按绑定时间升序）
     *
     * @param userId 用户 ID
     * @return 有效绑定列表
     */
    @Override
    public List<SocialBindingAggregate> listBoundByUser(Long userId) {
        return bindingMapper.selectList(new LambdaQueryWrapper<SysUserSocialBindingPo>()
                        .eq(SysUserSocialBindingPo::getUserId, userId)
                        .eq(SysUserSocialBindingPo::getStatus, SocialBindingAggregate.STATUS_BOUND)
                        .orderByAsc(SysUserSocialBindingPo::getCreateTime))
                .stream()
                .map(SocialBindingPersistenceAssembler::toDomain)
                .toList();
    }
}
