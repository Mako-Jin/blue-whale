package com.bluewhale.auth.application.port;

import com.bluewhale.auth.application.model.TenantPasswordPolicy;

import java.util.List;
import java.util.Optional;

/**
 * 租户口令策略仓储端口（REQ-SEC-023）。
 *
 * <p>应用层只声明「按租户读取 / 全量读取 / 保存」三个语义；具体落库由
 * {@code infrastructure} 适配实现。读取必须跨租户安全：提醒任务在全局租户上下文下运行，
 * 实现需显式按 {@code tenant_id} 定位记录，不能依赖会话租户上下文。</p>
 *
 * @author BlueWhale
 */
public interface TenantPasswordPolicyRepository {

    /**
     * 查询指定租户的自定义策略
     *
     * @param tenantId 租户 ID
     * @return 自定义策略；未配置时返回空
     */
    Optional<TenantPasswordPolicy> findByTenantId(Long tenantId);

    /**
     * 查询全部已配置的租户策略（供到期提醒任务按租户口径计算提醒窗口）
     *
     * @return 已配置策略列表；无配置时返回空列表
     */
    List<TenantPasswordPolicy> findAll();

    /**
     * 保存租户策略（不存在则新增，存在则更新）
     *
     * @param policy 生效策略配置
     * @return 保存后的策略配置
     */
    TenantPasswordPolicy save(TenantPasswordPolicy policy);
}
