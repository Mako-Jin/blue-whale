package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.command.CreateTenantCommand;
import com.bluewhale.auth.application.model.vo.TenantVO;

import java.util.List;
import java.util.Set;

/**
 * 租户开通应用服务（Wave 5.54 Phase 1）。
 *
 * <p><b>编排步骤</b>：创建租户 → 初始化部门根 → 复制平台菜单模板 → 创建默认角色 {@code tenant_admin}
 * 并授予除平台专属码外的全部权限 → 按平台业务角色模板下发业务角色 → 创建初始管理员并绑定默认角色。</p>
 *
 * <p><b>幂等与一致性</b>：① 分布式锁按租户编码串行，避免并发重复开通；
 * ② 全流程单事务，任一步失败整体回滚，重试从零开始，不会留下半初始化租户；
 * ③ {@code tenant_code} 唯一键作为最终兜底，重复开通直接快速失败。</p>
 *
 * @author BlueWhale
 */
public interface TenantProvisioningAppService {

    /**
     * 业务角色模板编码：模板由 {@code migration-5.65-business-role-grant.sql}、
     * {@code migration-5.66-pm-role-template.sql}、{@code migration-5.74-doc.sql}
     * 与 {@code V10__rbac_role_baseline.sql}（商城）在平台租户维护，新租户开通时按模板
     * 当前的角色名称/描述/排序与权限集下发同名角色。
     *
     * <p>模板缺失（未执行对应迁移）时仅告警跳过，不阻断租户开通；平台租户 {@code tenant_admin}
     * 仍持有全部启用业务权限，不会出现业务接口 403。</p>
     */
    public static final List<String> BUSINESS_ROLE_TEMPLATE_CODES = List.of(
            "archive_manager", "archive_member",
            "oa_manager", "oa_member",
            "erp_manager", "erp_member",
            "mes_manager", "mes_member",
            "pdm_manager", "pdm_member",
            "pm_manager", "pm_member",
            "contract_manager", "contract_member", "contract_signatory",
            "doc_manager", "doc_member",
            // 商城：whale-mall 的 MallActor 以角色码判定数据范围（canManageAll / isOperator），
            // 只授权限码不建角色时，运营动作对非超管永久不可用。模板见 V10__rbac_role_baseline.sql。
            "mall_admin", "mall_operator");

    /**
     * 平台侧专属权限码：不下发给业务租户。
     *
     * <p>只收口「平台运营」动作；{@code sys:tenant:member:list/assign} 属于租户管理员
     * 的本租户成员管理能力，必须正常下发。</p>
     *
     * <p><b>与迁移脚本的强耦合</b>：{@code V10__rbac_role_baseline.sql} 给平台租户的
     * {@code tenant_admin} 授权时用的是「全部启用权限 − 本集合」，两处必须一字不差。
     * 由 {@code FlywayMigrationScriptTest} 做一致性断言，改一处漏改另一处会被门禁拦下。</p>
     */
    public static final Set<String> PLATFORM_ONLY_PERM_CODES = Set.of(
            "sys:tenant:list", "sys:tenant:add", "sys:tenant:edit", "sys:tenant:status",
            "sys:tenant:renew", "sys:user:tenant");

    /**
     * 三员分立专属权限码：不下发给 {@code tenant_admin}，只授给对应三员角色。
     *
     * <p>等保 2.0 要求「管理员不得查看审计、审计员不得参与管理」。若 {@code tenant_admin} 保留
     * {@code sys:audit:log:list}，租户管理员就能查、甚至导出审计记录，职责分离失效。</p>
     *
     * <p><b>与迁移脚本的强耦合</b>：{@code V36__three_admin_segregation.sql} 第 4 段已从存量
     * {@code tenant_admin} 收回该权限，本集合保证新开通租户不再授予——改一处必须改两处。</p>
     */
    public static final Set<String> SEGREGATED_PERM_CODES = Set.of("sys:audit:log:list");

    /**
     * 开通租户：创建租户并完成管理员、默认角色、菜单与部门根初始化
     *
     * @param command 开通命令
     * @return 新租户视图
     */
    TenantVO provision(CreateTenantCommand command);
}
