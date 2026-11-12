package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.command.ChangeTenantStatusCommand;
import com.bluewhale.auth.application.model.command.RenewTenantCommand;
import com.bluewhale.auth.application.model.command.UpdateTenantCommand;
import com.bluewhale.auth.application.model.query.TenantPageQuery;
import com.bluewhale.auth.application.model.vo.TenantVO;
import com.bluewhale.auth.application.port.TenantStatusSyncPort;
import com.bluewhale.auth.domain.aggregate.Tenant;
import com.bluewhale.auth.domain.aggregate.TenantExpiryClock;
import com.bluewhale.auth.domain.repository.TenantRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
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
public interface TenantAppService {

    /** 登录未携带租户编码时回落的默认租户编码 */
    public static final String DEFAULT_TENANT_CODE = "default";

    /**
     * 登录租户解析：按租户编码定位「启用且未过期」的租户；编码为空回落默认租户
     *
     * @param tenantCode 登录请求携带的租户编码
     * @return 可用租户 ID
     */
    Long resolveEnabledTenantId(String tenantCode);

    /**
     * 校验租户当前可用（启用且未过期），用于刷新令牌等仅携带租户 ID 的链路
     *
     * @param tenantId 租户 ID
     */
    void ensureEnabled(Long tenantId);

    /**
     * 查询全部可用租户 ID（启用且未过期），供后台定时任务按租户游标执行
     *
     * <p>内网接口，仅服务间直连调用，不做平台租户校验。</p>
     *
     * @return 可用租户 ID（升序）
     */
    List<Long> listEnabledTenantIds();

    /**
     * 租户分页列表（仅平台租户）
     *
     * @param query 分页查询条件
     * @return 分页结果
     */
    PageResult<TenantVO> page(TenantPageQuery query);

    /**
     * 租户详情（仅平台租户）
     *
     * @param id 租户 ID
     * @return 租户视图
     */
    TenantVO getDetail(Long id);

    /**
     * 更新租户名称与配额（仅平台租户）
     *
     * @param id 租户 ID
     * @param command 更新命令
     * @return 更新后的租户视图
     */
    TenantVO update(Long id, UpdateTenantCommand command);

    /**
     * 启用/停用租户（仅平台租户；平台租户自身不允许停用）
     *
     * @param id 租户 ID
     * @param command 启停命令
     * @return 变更后的租户视图
     */
    TenantVO changeStatus(Long id, ChangeTenantStatusCommand command);

    /**
     * 租户续期（仅平台租户）
     *
     * @param id 租户 ID
     * @param command 续期命令
     * @return 续期后的租户视图
     */
    TenantVO renew(Long id, RenewTenantCommand command);

    /**
     * 校验并返回当前租户为平台运营租户（租户管理用例的统一准入条件）
     *
     * @return 当前平台租户
     */
    Tenant requirePlatformTenant();
}
