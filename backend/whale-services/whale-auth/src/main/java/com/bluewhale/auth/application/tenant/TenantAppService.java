package com.bluewhale.auth.application.tenant;

import com.bluewhale.auth.application.tenant.command.ChangeTenantStatusCommand;
import com.bluewhale.auth.application.tenant.command.RenewTenantCommand;
import com.bluewhale.auth.application.tenant.command.UpdateTenantCommand;
import com.bluewhale.auth.application.tenant.query.TenantPageQuery;
import com.bluewhale.auth.domain.tenant.aggregate.Tenant;
import com.bluewhale.auth.domain.tenant.repository.TenantRepository;
import com.bluewhale.auth.security.TenantContext;
import com.bluewhale.auth.vo.TenantVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户应用服务（Wave 5.54 Phase 1）：登录租户解析、租户生命周期与平台侧租户管理用例编排。
 *
 * <p>租户管理类用例统一要求「当前租户为平台运营租户」，非平台租户一律 403。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantAppService {

    /** 登录未携带租户编码时回落的默认租户编码 */
    public static final String DEFAULT_TENANT_CODE = "default";

    private final TenantRepository tenantRepository;

    /**
     * 登录租户解析：按租户编码定位「启用且未过期」的租户；编码为空回落默认租户
     *
     * @param tenantCode 登录请求携带的租户编码
     * @return 可用租户 ID
     */
    public Long resolveEnabledTenantId(String tenantCode) {
        String code = StringUtils.hasText(tenantCode) ? tenantCode.trim() : DEFAULT_TENANT_CODE;
        Tenant tenant = tenantRepository.findByCode(code)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED.getCode(), "租户不存在或已停用"));
        return requireAvailable(tenant);
    }

    /**
     * 校验租户当前可用（启用且未过期），用于刷新令牌等仅携带租户 ID 的链路
     *
     * @param tenantId 租户 ID
     */
    public void ensureEnabled(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BizException(401, "租户不存在或已停用"));
        requireAvailable(tenant);
    }

    /**
     * 租户分页列表（仅平台租户）
     *
     * @param query 分页查询条件
     * @return 分页结果
     */
    public PageResult<TenantVO> page(TenantPageQuery query) {
        requirePlatformTenant();
        String keyword = query.keyword() == null ? null : query.keyword().trim().toLowerCase();
        List<Tenant> matched = tenantRepository.findAll().stream()
                .filter(tenant -> query.status() == null || tenant.getStatus() == query.status())
                .filter(tenant -> !StringUtils.hasText(keyword)
                        || tenant.getTenantCode().toLowerCase().contains(keyword)
                        || tenant.getTenantName().toLowerCase().contains(keyword))
                .toList();
        long current = Math.max(query.current(), 1);
        long size = query.size() <= 0 ? 10 : query.size();
        int from = (int) Math.min((current - 1) * size, matched.size());
        int to = (int) Math.min(from + size, matched.size());
        List<TenantVO> records = matched.subList(from, to).stream().map(TenantVO::from).toList();
        return PageResult.of(records, (long) matched.size(), current, size);
    }

    /**
     * 租户详情（仅平台租户）
     *
     * @param id 租户 ID
     * @return 租户视图
     */
    public TenantVO getDetail(Long id) {
        requirePlatformTenant();
        return TenantVO.from(requireTenant(id));
    }

    /**
     * 更新租户名称与配额（仅平台租户）
     *
     * @param id 租户 ID
     * @param command 更新命令
     * @return 更新后的租户视图
     */
    @Transactional
    public TenantVO update(Long id, UpdateTenantCommand command) {
        requirePlatformTenant();
        Tenant tenant = requireTenant(id);
        if (StringUtils.hasText(command.tenantName())) {
            tenant.rename(command.tenantName().trim());
        }
        if (command.quotaJson() != null) {
            tenant.changeQuota(command.quotaJson());
        }
        return TenantVO.from(tenantRepository.save(tenant));
    }

    /**
     * 启用/停用租户（仅平台租户；平台租户自身不允许停用）
     *
     * @param id 租户 ID
     * @param command 启停命令
     * @return 变更后的租户视图
     */
    @Transactional
    public TenantVO changeStatus(Long id, ChangeTenantStatusCommand command) {
        requirePlatformTenant();
        Integer status = command.status();
        if (status == null || (status != Tenant.STATUS_ENABLED && status != Tenant.STATUS_DISABLED)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
        Tenant tenant = requireTenant(id);
        if (tenant.isPlatform() && status == Tenant.STATUS_DISABLED) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "平台租户不允许停用");
        }
        if (status == Tenant.STATUS_ENABLED) {
            tenant.enable();
        } else {
            tenant.disable();
        }
        Tenant saved = tenantRepository.save(tenant);
        log.info("租户状态变更: tenantId={}, status={}", id, status);
        return TenantVO.from(saved);
    }

    /**
     * 租户续期（仅平台租户）
     *
     * @param id 租户 ID
     * @param command 续期命令
     * @return 续期后的租户视图
     */
    @Transactional
    public TenantVO renew(Long id, RenewTenantCommand command) {
        requirePlatformTenant();
        Tenant tenant = requireTenant(id);
        tenant.renew(command.expireTime());
        return TenantVO.from(tenantRepository.save(tenant));
    }

    /**
     * 校验并返回当前租户为平台运营租户（租户管理用例的统一准入条件）
     *
     * @return 当前平台租户
     */
    public Tenant requirePlatformTenant() {
        Long tenantId = TenantContext.requireId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED.getCode(), "租户不存在"));
        if (!tenant.isPlatform()) {
            throw new BizException(403, "仅平台租户可管理租户");
        }
        return tenant;
    }

    /**
     * 校验租户可用并返回租户 ID
     *
     * @param tenant 租户聚合
     * @return 租户 ID
     */
    private Long requireAvailable(Tenant tenant) {
        if (!tenant.isAvailableAt(LocalDateTime.now())) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "租户已停用或已过期");
        }
        return tenant.getId();
    }

    /**
     * 加载租户，不存在时 404
     *
     * @param id 租户 ID
     * @return 租户聚合
     */
    private Tenant requireTenant(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "租户不存在"));
    }
}
