package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.TenantPasswordPolicy;
import com.bluewhale.auth.application.model.command.UpdateTenantPasswordPolicyCommand;
import com.bluewhale.auth.application.model.vo.TenantPasswordPolicyVO;
import com.bluewhale.auth.application.port.PasswordPolicyConfig;
import com.bluewhale.auth.application.port.TenantPasswordPolicyRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 租户口令策略服务（REQ-SEC-023）：租户管理员配置本租户口令有效期，未配置时回退全局默认。
 *
 * <p><b>生效口径</b>：登录/刷新签发令牌时按用户所属租户读取有效期判定是否过期；
 * 到期提醒任务按每个启用租户各自的策略计算提醒窗口与剩余天数，避免全局配置一刀切。</p>
 *
 * <p><b>安全基线</b>：租户可配置范围为 1–365 天，不允许通过租户配置完全关闭口令到期；
 * 应急关闭仍由全局 {@code whale.auth.password.max-age-days<=0} 控制（仅运维可用）。</p>
 *
 * @author BlueWhale
 */
public interface TenantPasswordPolicyService {

    /** 租户可配置的最短有效期（天） */
    public static final int MIN_MAX_AGE_DAYS = 1;

    /** 租户可配置的最长有效期（天） */
    public static final int MAX_MAX_AGE_DAYS = 365;

    /**
     * 查询当前登录租户的口令策略（含全局默认值，供管理端展示）
     *
     * @return 当前租户生效策略视图
     */
    public TenantPasswordPolicyVO current();

    /**
     * 查询指定租户生效策略（含全局默认值）
     *
     * @param tenantId 租户 ID
     * @return 租户生效策略视图
     */
    public TenantPasswordPolicyVO getEffective(Long tenantId);

    /**
     * 获取指定租户的生效策略（租户未配置时回退全局默认）
     *
     * @param tenantId 租户 ID
     * @return 生效策略
     */
    public TenantPasswordPolicy effectivePolicy(Long tenantId);

    /**
     * 获取指定租户的生效口令有效期（供登录/刷新到期判定）
     *
     * @param tenantId 租户 ID
     * @return 有效期天数；小于等于 0 表示不强制到期
     */
    public int maxAgeDays(Long tenantId);

    /**
     * 计算全部启用租户的生效策略（供到期提醒任务按租户口径扫描）
     *
     * @return 租户 ID → 生效策略；无启用租户时返回空 Map
     */
    public Map<Long, TenantPasswordPolicy> effectivePolicies();

    /**
     * 更新当前租户的口令策略（租户管理员操作，按审计注解留痕）
     *
     * @param command 更新命令
     * @return 更新后的生效策略视图（{@code customized=true}）
     */
    public TenantPasswordPolicyVO update(UpdateTenantPasswordPolicyCommand command);
}
