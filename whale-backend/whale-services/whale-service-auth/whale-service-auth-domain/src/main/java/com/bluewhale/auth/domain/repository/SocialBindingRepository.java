package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.SocialBindingAggregate;
import com.bluewhale.auth.domain.model.vo.SocialProvider;

import java.util.List;
import java.util.Optional;

/**
 * 三方登录绑定仓储端口（权限中心扩展 M2）：契约定义于领域层，由 infrastructure 适配实现。
 *
 * @author BlueWhale
 */
public interface SocialBindingRepository {

    /**
     * 保存绑定聚合（无 ID 时新增，有 ID 时按主键更新）
     *
     * @param binding 绑定聚合
     * @return 回填主键后的聚合
     */
    SocialBindingAggregate save(SocialBindingAggregate binding);

    /**
     * 按主键查询
     *
     * @param id 主键
     * @return 绑定聚合；不存在时返回空
     */
    Optional<SocialBindingAggregate> findById(Long id);

    /**
     * 按三方身份查询绑定（含已解绑记录，调用方自行判断 {@code isBound()}）
     *
     * <p>{@code (provider, providerUserId)} 全局唯一，命中多条视为数据异常，实现侧取最新一条。</p>
     *
     * @param provider 三方渠道
     * @param providerUserId 三方用户标识
     * @return 绑定聚合；不存在时返回空
     */
    Optional<SocialBindingAggregate> findByProviderUser(SocialProvider provider, String providerUserId);

    /**
     * 查询用户在某渠道下的有效绑定（一个用户同一渠道至多一条）
     *
     * @param userId 用户 ID
     * @param provider 三方渠道
     * @return 绑定聚合；不存在或已解绑时返回空
     */
    Optional<SocialBindingAggregate> findBoundByUserAndProvider(Long userId, SocialProvider provider);

    /**
     * 查询用户的全部有效绑定（账号设置页展示）
     *
     * @param userId 用户 ID
     * @return 有效绑定列表（按绑定时间升序）
     */
    List<SocialBindingAggregate> listBoundByUser(Long userId);
}
