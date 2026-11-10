package com.bluewhale.auth.application.user.password;

import com.bluewhale.auth.application.tenant.TenantAppService;
import com.bluewhale.auth.application.user.password.port.PasswordPolicyConfig;
import com.bluewhale.auth.application.user.password.port.TenantPasswordPolicyRepository;
import com.bluewhale.auth.application.vo.TenantPasswordPolicyVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
@Service
@RequiredArgsConstructor
public class TenantPasswordPolicyService {

    /** 租户可配置的最短有效期（天） */
    public static final int MIN_MAX_AGE_DAYS = 1;

    /** 租户可配置的最长有效期（天） */
    public static final int MAX_MAX_AGE_DAYS = 365;

    /** 全局口令策略配置（默认值来源） */
    private final PasswordPolicyConfig properties;

    /** 租户口令策略仓储端口 */
    private final TenantPasswordPolicyRepository tenantPasswordPolicyRepository;

    /** 租户应用服务（提供启用租户列表，供提醒任务按租户计算） */
    private final TenantAppService tenantAppService;

    /**
     * 查询当前登录租户的口令策略（含全局默认值，供管理端展示）
     *
     * @return 当前租户生效策略视图
     */
    public TenantPasswordPolicyVO current() {
        return viewOf(TenantContext.requireId());
    }

    /**
     * 查询指定租户生效策略（含全局默认值）
     *
     * @param tenantId 租户 ID
     * @return 租户生效策略视图
     */
    public TenantPasswordPolicyVO getEffective(Long tenantId) {
        return viewOf(tenantId);
    }

    /**
     * 获取指定租户的生效策略（租户未配置时回退全局默认）
     *
     * @param tenantId 租户 ID
     * @return 生效策略
     */
    public TenantPasswordPolicy effectivePolicy(Long tenantId) {
        if (tenantId == null) {
            return defaultPolicy(null);
        }
        return tenantPasswordPolicyRepository.findByTenantId(tenantId)
                .orElseGet(() -> defaultPolicy(tenantId));
    }

    /**
     * 获取指定租户的生效口令有效期（供登录/刷新到期判定）
     *
     * @param tenantId 租户 ID
     * @return 有效期天数；小于等于 0 表示不强制到期
     */
    public int maxAgeDays(Long tenantId) {
        return effectivePolicy(tenantId).maxAgeDays();
    }

    /**
     * 计算全部启用租户的生效策略（供到期提醒任务按租户口径扫描）
     *
     * @return 租户 ID → 生效策略；无启用租户时返回空 Map
     */
    public Map<Long, TenantPasswordPolicy> effectivePolicies() {
        Map<Long, TenantPasswordPolicy> configured = tenantPasswordPolicyRepository.findAll().stream()
                .collect(Collectors.toMap(TenantPasswordPolicy::tenantId, policy -> policy));
        Map<Long, TenantPasswordPolicy> effective = new LinkedHashMap<>();
        for (Long tenantId : tenantAppService.listEnabledTenantIds()) {
            effective.put(tenantId, configured.getOrDefault(tenantId, defaultPolicy(tenantId)));
        }
        return effective;
    }

    /**
     * 更新当前租户的口令策略（租户管理员操作，按审计注解留痕）
     *
     * @param command 更新命令
     * @return 更新后的生效策略视图（{@code customized=true}）
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantPasswordPolicyVO update(UpdateTenantPasswordPolicyCommand command) {
        Long tenantId = TenantContext.requireId();
        validate(command);
        TenantPasswordPolicy saved = tenantPasswordPolicyRepository.save(
                new TenantPasswordPolicy(tenantId, command.maxAgeDays(), command.warnDays()));
        return new TenantPasswordPolicyVO(tenantId, saved.maxAgeDays(), saved.warnDays(), true,
                properties.getMaxAgeDays(), properties.getWarnDays());
    }

    /**
     * 组装租户生效策略视图
     *
     * @param tenantId 租户 ID
     * @return 生效策略视图（含是否自定义与全局默认值）
     */
    private TenantPasswordPolicyVO viewOf(Long tenantId) {
        Optional<TenantPasswordPolicy> configured = tenantPasswordPolicyRepository.findByTenantId(tenantId);
        TenantPasswordPolicy effective = configured.orElseGet(() -> defaultPolicy(tenantId));
        return new TenantPasswordPolicyVO(tenantId, effective.maxAgeDays(), effective.warnDays(),
                configured.isPresent(), properties.getMaxAgeDays(), properties.getWarnDays());
    }

    /**
     * 校验租户可配置范围：有效期 1–365 天，提醒天数 0–有效期
     *
     * @param command 更新命令
     */
    private static void validate(UpdateTenantPasswordPolicyCommand command) {
        if (command.maxAgeDays() == null || command.warnDays() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "口令有效期与提醒天数不能为空");
        }
        int maxAgeDays = command.maxAgeDays();
        int warnDays = command.warnDays();
        if (maxAgeDays < MIN_MAX_AGE_DAYS || maxAgeDays > MAX_MAX_AGE_DAYS) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "口令有效期必须在 " + MIN_MAX_AGE_DAYS + "–" + MAX_MAX_AGE_DAYS + " 天之间");
        }
        if (warnDays < 0 || warnDays > maxAgeDays) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "到期提醒天数必须在 0 到口令有效期之间");
        }
    }

    /**
     * 构造全局默认策略
     *
     * @param tenantId 租户 ID（可为空，表示与租户无关的默认值）
     * @return 默认策略
     */
    private TenantPasswordPolicy defaultPolicy(Long tenantId) {
        return new TenantPasswordPolicy(tenantId, properties.getMaxAgeDays(), properties.getWarnDays());
    }
}
