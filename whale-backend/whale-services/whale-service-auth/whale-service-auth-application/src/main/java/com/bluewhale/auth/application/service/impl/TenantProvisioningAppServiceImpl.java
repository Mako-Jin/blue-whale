package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.command.CreateTenantCommand;
import com.bluewhale.auth.application.model.vo.TenantVO;
import com.bluewhale.auth.application.port.PasswordHasher;
import com.bluewhale.auth.application.port.TenantStatusSyncPort;
import com.bluewhale.auth.application.service.TenantAppService;
import com.bluewhale.auth.application.service.TenantMembershipAppService;
import com.bluewhale.auth.application.service.TenantProvisioningAppService;
import com.bluewhale.auth.domain.aggregate.DeptAggregate;
import com.bluewhale.auth.domain.model.AuthRoleCodes;
import com.bluewhale.auth.domain.repository.DeptRepository;
import com.bluewhale.auth.domain.aggregate.MenuAggregate;
import com.bluewhale.auth.domain.repository.MenuRepository;
import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.RoleCode;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.aggregate.PermissionAggregate;
import com.bluewhale.auth.domain.repository.PermissionRepository;
import com.bluewhale.auth.domain.aggregate.RoleAggregate;
import com.bluewhale.auth.domain.repository.RoleRepository;
import com.bluewhale.auth.domain.aggregate.TenantAggregate;
import com.bluewhale.auth.domain.repository.TenantRepository;
import com.bluewhale.auth.domain.aggregate.UserAggregate;
import com.bluewhale.auth.domain.repository.UserRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.lock.annotation.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningAppServiceImpl implements TenantProvisioningAppService {

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

    private final TenantAppService tenantAppService;
    private final TenantRepository tenantRepository;
    private final DeptRepository deptRepository;
    private final MenuRepository menuRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordHasher passwordHasher;
    private final TenantStatusSyncPort tenantStatusSync;
    private final TenantMembershipAppService tenantMembershipAppService;

    /**
     * 开通租户：创建租户并完成管理员、默认角色、菜单与部门根初始化
     *
     * @param command 开通命令
     * @return 新租户视图
     */
    @DistributedLock(biz = "auth:tenant:provision", key = "#command.tenantCode()", waitTime = 3, leaseTime = 60)
    @Transactional(rollbackFor = Exception.class)
    public TenantVO provision(CreateTenantCommand command) {
        tenantAppService.requirePlatformTenant();
        String tenantCode = command.tenantCode() == null ? null : command.tenantCode().trim();
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "租户编码不能为空");
        }
        if (tenantRepository.findByCode(tenantCode).isPresent()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "租户编码已存在");
        }
        Username adminUsername = Username.of(command.adminUsername());
        TenantAggregate tenant = tenantRepository.save(
                TenantAggregate.create(tenantCode, command.tenantName(), command.expireTime(), command.quotaJson()));
        Long tenantId = tenant.getId();

        // 1. 部门根：以租户名称作为根部门，供管理员与后续用户挂载
        DeptAggregate rootDept = deptRepository.save(
                DeptAggregate.create(tenantId, 0L, tenant.getTenantName(), 0, null));

        // 2. 菜单模板：从平台租户复制，保持父子层级
        copyMenusFromPlatform(tenantId);

        // 3. 默认角色：租户管理员，授予除平台租户管理外的全部启用权限
        List<PermissionAggregate> tenantGrantablePermissions = permissionRepository.findEnabledAll().stream()
                .filter(permission -> !PLATFORM_ONLY_PERM_CODES.contains(permission.getPermCode().value()))
                .toList();
        List<Long> permissionIds = tenantGrantablePermissions.stream()
                .map(PermissionAggregate::getId)
                .toList();
        RoleAggregate adminRole = roleRepository.save(RoleAggregate.create(tenantId, RoleCode.of(AuthRoleCodes.TENANT_ADMIN),
                "租户管理员", "租户开通时自动创建，管理本租户用户、角色、菜单与部门", 0, RoleAggregate.STATUS_ENABLED));
        roleRepository.replacePermissions(adminRole.getId(), adminRole.assignPermissions(permissionIds));

        // 4. 业务角色：按平台租户模板下发同名角色并复制模板当前权限集
        copyBusinessRolesFromPlatform(tenantId, tenantGrantablePermissions);

        // 5. 初始管理员：归属根部门并绑定默认角色
        UserAggregate admin = userRepository.save(UserAggregate.create(tenantId, adminUsername,
                EncryptedPassword.of(passwordHasher.encode(command.rawPassword())), command.adminNickname(),
                null, null, null, rootDept.getId(), UserAggregate.STATUS_ENABLED));
        userRepository.replaceRoles(admin.getId(), admin.assignRoles(List.of(adminRole.getId())));
        // 一人多租户：初始管理员的首页租户即新开通租户
        tenantMembershipAppService.ensureHomeMembership(admin.getId(), tenantId);
        tenantStatusSync.sync(tenant);

        log.info("租户开通完成: tenantId={}, tenantCode={}, adminUserId={}, permissionCount={}",
                tenantId, tenantCode, admin.getId(), permissionIds.size());
        return TenantVO.from(tenant);
    }

    /**
     * 按平台租户业务角色模板向新租户下发业务角色。
     *
     * <p><b>模板来源</b>：平台租户（{@link TenantAggregate#PLATFORM_TENANT_ID}）下由
     * {@code migration-5.65-business-role-grant.sql} 与
     * {@code migration-5.66-pm-role-template.sql} 维护的同名角色；角色名称、描述、排序与状态原样复制，
     * 权限集取模板当前的 {@code sys_role_permission}，再与本次下发给业务租户的启用权限集合取交集。</p>
     *
     * <p><b>缺失处理</b>：模板未部署（未执行 5.65/5.66）时逐项告警并跳过，不阻断租户开通；
     * 新租户的 {@code tenant_admin} 仍持有全部启用业务权限。</p>
     *
     * @param targetTenantId 新租户 ID
     * @param grantablePermissions 可下发给业务租户的启用权限（已剔除平台专属码）
     */
    private void copyBusinessRolesFromPlatform(Long targetTenantId, List<PermissionAggregate> grantablePermissions) {
        Set<Long> grantablePermissionIds = grantablePermissions.stream()
                .map(PermissionAggregate::getId)
                .collect(Collectors.toSet());
        int copied = 0;
        int missing = 0;
        for (String roleCode : BUSINESS_ROLE_TEMPLATE_CODES) {
            Optional<RoleAggregate> template = roleRepository.findByTenantIdAndCode(TenantAggregate.PLATFORM_TENANT_ID, roleCode);
            if (template.isEmpty()) {
                missing++;
                log.warn("业务角色模板缺失，跳过下发: tenantId={}, roleCode={}", targetTenantId, roleCode);
                continue;
            }
            RoleAggregate source = template.get();
            RoleAggregate role = roleRepository.save(RoleAggregate.create(targetTenantId, RoleCode.of(roleCode),
                    source.getRoleName(), source.getDescription(), source.getSortNo(), source.getStatus()));
            List<Long> templatePermissionIds = roleRepository.listPermissionIds(source.getId());
            List<Long> permissionIds = templatePermissionIds.stream()
                    .filter(grantablePermissionIds::contains)
                    .toList();
            roleRepository.replacePermissions(role.getId(), role.assignPermissions(permissionIds));
            copied++;
        }
        log.info("业务角色模板下发完成: tenantId={}, copied={}, missing={}, total={}",
                targetTenantId, copied, missing, BUSINESS_ROLE_TEMPLATE_CODES.size());
    }

    /**
     * 复制平台租户菜单到目标租户，按层级逐轮落库并建立新旧 ID 映射
     *
     * @param targetTenantId 目标租户 ID
     */
    private void copyMenusFromPlatform(Long targetTenantId) {
        List<MenuAggregate> remaining = new ArrayList<>(
                menuRepository.findAllByTenantId(TenantAggregate.PLATFORM_TENANT_ID));
        Map<Long, Long> idMapping = new HashMap<>();
        while (!remaining.isEmpty()) {
            boolean progressed = false;
            Iterator<MenuAggregate> iterator = remaining.iterator();
            while (iterator.hasNext()) {
                MenuAggregate source = iterator.next();
                // 父节点尚未映射时 get() 返回 null，属于「本轮跳过、下一轮重试」的正常情形；
                // parentId 判等必须用 Long.equals，避免 Long 与原始字面量比较触发自动拆箱 NPE
                Long mappedParentId = Long.valueOf(MenuAggregate.ROOT_PARENT_ID).equals(source.getParentId())
                        ? Long.valueOf(MenuAggregate.ROOT_PARENT_ID) : idMapping.get(source.getParentId());
                if (mappedParentId == null) {
                    continue;
                }
                MenuAggregate copied = menuRepository.save(MenuAggregate.create(targetTenantId, mappedParentId,
                        source.getName(), source.getPath(), source.getComponent(), source.getIcon(),
                        source.getSort(), source.getMenuType(), source.getPermissionCode(),
                        source.getVisible(), source.getStatus()));
                idMapping.put(source.getId(), copied.getId());
                iterator.remove();
                progressed = true;
            }
            if (!progressed) {
                log.warn("菜单模板存在无法解析的父子层级，跳过剩余菜单: tenantId={}, remain={}",
                        targetTenantId, remaining.size());
                break;
            }
        }
    }
}
