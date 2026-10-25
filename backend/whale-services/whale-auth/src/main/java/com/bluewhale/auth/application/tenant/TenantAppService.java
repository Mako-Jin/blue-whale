package com.bluewhale.auth.application.tenant;

import com.bluewhale.auth.domain.tenant.aggregate.Tenant;
import com.bluewhale.auth.domain.tenant.repository.TenantRepository;
import com.bluewhale.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 租户应用服务（Wave 5.54 Phase 0）：登录与令牌刷新时的租户解析与启用校验。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class TenantAppService {

    /** 默认租户编码：登录未填写租户码时使用，兼容存量单租户客户端 */
    public static final String DEFAULT_TENANT_CODE = "default";

    /** 租户不可用统一文案，避免泄露租户是否存在 */
    private static final String TENANT_UNAVAILABLE_MESSAGE = "租户不存在或已停用";

    private final TenantRepository tenantRepository;

    /**
     * 由租户编码解析启用的租户 ID；编码为空时回落默认租户
     *
     * @param tenantCode 租户编码（可空）
     * @return 启用的租户 ID
     * @throws BizException 租户不存在或已停用时抛出 401
     */
    public Long resolveEnabledTenantId(String tenantCode) {
        String code = StringUtils.hasText(tenantCode) ? tenantCode.trim() : DEFAULT_TENANT_CODE;
        Tenant tenant = tenantRepository.findByCode(code)
                .orElseThrow(() -> new BizException(401, TENANT_UNAVAILABLE_MESSAGE));
        if (!tenant.isEnabled()) {
            throw new BizException(401, TENANT_UNAVAILABLE_MESSAGE);
        }
        return tenant.getId();
    }

    /**
     * 校验租户存在且启用
     *
     * @param tenantId 租户 ID
     * @throws BizException 租户不存在或已停用时抛出 401
     */
    public void ensureEnabled(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BizException(401, TENANT_UNAVAILABLE_MESSAGE));
        if (!tenant.isEnabled()) {
            throw new BizException(401, TENANT_UNAVAILABLE_MESSAGE);
        }
    }
}
