package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.auth.domain.model.vo.ProviderUserId;
import com.bluewhale.auth.domain.model.vo.SocialProvider;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;

/**
 * 三方登录绑定聚合根（对应 {@code sys_user_social_binding}，权限中心扩展 M2）。
 *
 * <p><b>不变量</b>：① {@code (provider, providerUserId)} 全局唯一（由仓储前置检查 +
 * DB 唯一约束兜底）；② 一个用户在同一 provider 下至多一条有效绑定；③ 解绑后
 * （{@code status=0}）不得再作为登录入口，只能重新绑定。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。</p>
 *
 * @author BlueWhale
 */
public class SocialBindingAggregate implements Entity<Long> {

    /** 状态：绑定生效 */
    public static final int STATUS_BOUND = 1;
    /** 状态：已解绑 */
    public static final int STATUS_UNBOUND = 0;

    private final Long id;
    private final Long tenantId;
    private final Long userId;
    private final SocialProvider provider;
    private final ProviderUserId providerUserId;
    private String unionId;
    private String nickname;
    private String avatar;
    private int status;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验聚合不变量
     *
     * @param id 主键（新建时为 {@code null}）
     * @param tenantId 用户归属租户 ID
     * @param userId 绑定用户 ID
     * @param provider 三方渠道
     * @param providerUserId 三方用户标识
     * @param unionId 三方 unionId（可空）
     * @param nickname 三方昵称快照（可空）
     * @param avatar 三方头像快照（可空）
     * @param status 状态
     * @param createTime 绑定时间
     * @param updateTime 更新时间
     */
    private SocialBindingAggregate(Long id, Long tenantId, Long userId, SocialProvider provider,
                                   ProviderUserId providerUserId, String unionId, String nickname,
                                   String avatar, int status, LocalDateTime createTime,
                                   LocalDateTime updateTime) {
        if (tenantId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "绑定必须归属租户");
        }
        if (userId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "绑定必须归属用户");
        }
        if (provider == null || providerUserId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "三方渠道与用户标识不能为空");
        }
        if (status != STATUS_BOUND && status != STATUS_UNBOUND) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（绑定）/ 0（已解绑）");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.unionId = unionId;
        this.nickname = nickname;
        this.avatar = avatar;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：建立绑定（默认生效）
     *
     * @param tenantId 用户归属租户 ID
     * @param userId 绑定用户 ID
     * @param provider 三方渠道
     * @param providerUserId 三方用户标识
     * @param unionId 三方 unionId（可空）
     * @param nickname 三方昵称快照（可空）
     * @param avatar 三方头像快照（可空）
     * @return 新建绑定聚合
     */
    public static SocialBindingAggregate create(Long tenantId, Long userId, SocialProvider provider,
                                                String providerUserId, String unionId,
                                                String nickname, String avatar) {
        LocalDateTime now = LocalDateTime.now();
        return new SocialBindingAggregate(null, tenantId, userId, provider, ProviderUserId.of(providerUserId),
                unionId, nickname, avatar, STATUS_BOUND, now, now);
    }

    /**
     * 由持久化数据重建聚合
     *
     * @param id 主键
     * @param tenantId 用户归属租户 ID
     * @param userId 绑定用户 ID
     * @param provider 渠道编码
     * @param providerUserId 三方用户标识
     * @param unionId 三方 unionId
     * @param nickname 昵称快照
     * @param avatar 头像快照
     * @param status 状态
     * @param createTime 绑定时间
     * @param updateTime 更新时间
     * @return 绑定聚合
     */
    public static SocialBindingAggregate reconstitute(Long id, Long tenantId, Long userId, String provider,
                                                      String providerUserId, String unionId, String nickname,
                                                      String avatar, Integer status,
                                                      LocalDateTime createTime, LocalDateTime updateTime) {
        return new SocialBindingAggregate(id, tenantId, userId, SocialProvider.of(provider),
                ProviderUserId.of(providerUserId), unionId, nickname, avatar,
                status == null ? STATUS_UNBOUND : status, createTime, updateTime);
    }

    /**
     * 行为：解绑（解绑后不得再作为登录入口；同一 (provider, providerUserId) 不可复用，
     * 重新绑定会因唯一约束冲突，用户需先解绑三方侧旧账号或由原账号重绑）
     */
    public void unbind() {
        if (status == STATUS_UNBOUND) {
            return;
        }
        this.status = STATUS_UNBOUND;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：刷新三方资料快照（昵称 / 头像），unionId 缺失时补全
     *
     * @param unionId 三方 unionId（可空，非空时覆盖）
     * @param nickname 最新昵称（可空，非空时覆盖）
     * @param avatar 最新头像（可空，非空时覆盖）
     */
    public void refreshProfile(String unionId, String nickname, String avatar) {
        if (unionId != null && !unionId.isBlank()) {
            this.unionId = unionId.trim();
        }
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname.trim();
        }
        if (avatar != null && !avatar.isBlank()) {
            this.avatar = avatar.trim();
        }
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 判断绑定是否生效
     *
     * @return 生效返回 {@code true}
     */
    public boolean isBound() {
        return status == STATUS_BOUND;
    }

    /**
     * 判断是否属于指定用户
     *
     * @param targetUserId 目标用户 ID
     * @return 属于返回 {@code true}
     */
    public boolean belongsTo(Long targetUserId) {
        return userId != null && userId.equals(targetUserId);
    }

    /**
     * 获取主键
     *
     * @return 主键
     */
    @Override
    public Long getId() {
        return id;
    }

    /**
     * 获取用户归属租户 ID
     *
     * @return 租户 ID
     */
    public Long getTenantId() {
        return tenantId;
    }

    /**
     * 获取绑定用户 ID
     *
     * @return 用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 获取三方渠道
     *
     * @return 渠道枚举
     */
    public SocialProvider getProvider() {
        return provider;
    }

    /**
     * 获取三方用户标识
     *
     * @return 标识值对象
     */
    public ProviderUserId getProviderUserId() {
        return providerUserId;
    }

    /**
     * 获取三方 unionId
     *
     * @return unionId（可空）
     */
    public String getUnionId() {
        return unionId;
    }

    /**
     * 获取昵称快照
     *
     * @return 昵称（可空）
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * 获取头像快照
     *
     * @return 头像 URL（可空）
     */
    public String getAvatar() {
        return avatar;
    }

    /**
     * 获取状态
     *
     * @return 状态（1 绑定 / 0 已解绑）
     */
    public int getStatus() {
        return status;
    }

    /**
     * 获取绑定时间
     *
     * @return 绑定时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
}
