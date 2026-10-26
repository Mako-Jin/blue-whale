package com.bluewhale.auth.application.tenant;

import com.bluewhale.auth.application.tenant.command.CreateTenantCommand;
import com.bluewhale.auth.domain.dept.aggregate.Dept;
import com.bluewhale.auth.domain.dept.repository.DeptRepository;
import com.bluewhale.auth.domain.menu.aggregate.Menu;
import com.bluewhale.auth.domain.menu.repository.MenuRepository;
import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.RoleCode;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.permission.aggregate.Permission;
import com.bluewhale.auth.domain.permission.repository.PermissionRepository;
import com.bluewhale.auth.domain.role.aggregate.Role;
import com.bluewhale.auth.domain.role.repository.RoleRepository;
import com.bluewhale.auth.domain.tenant.aggregate.Tenant;
import com.bluewhale.auth.domain.tenant.repository.TenantRepository;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.auth.vo.TenantVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.lock.annotation.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 租户开通应用服务（Wave 5.54 Phase 1）。
 *
 * <p><b>编排步骤</b>：创建租户 → 初始化部门根 → 复制平台菜单模板 → 创建默认角色 {@code tenant_admin}
 * 并授予除平台专属码外的全部权限 → 创建初始管理员并绑定默认角色。</p>
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
public class TenantProvisioningAppService {

    /** 新租户默认管理员角色编码 */
    public static final String DEFAULT_TENANT_ADMIN_ROLE = "tenant_admin";
    /**
     * 平台侧专属权限码：不下发给业务租户。
     *
     * <p>只收口「平台运营」动作；{@code sys:tenant:member:list/assign} 属于租户管理员
     * 的本租户成员管理能力，必须正常下发。</p>
     */
    private static final Set<String> PLATFORM_ONLY_PERM_CODES = Set.of(
            "sys:tenant:list", "sys:tenant:add", "sys:tenant:edit", "sys:tenant:status",
            "sys:tenant:renew", "sys:user:tenant");

    private final TenantAppService tenantAppService;
    private final TenantRepository tenantRepository;
    private final DeptRepository deptRepository;
    private final MenuRepository menuRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantStatusCache tenantStatusCache;
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
        Tenant tenant = tenantRepository.save(
                Tenant.create(tenantCode, command.tenantName(), command.expireTime(), command.quotaJson()));
        Long tenantId = tenant.getId();

        // 1. 部门根：以租户名称作为根部门，供管理员与后续用户挂载
        Dept rootDept = deptRepository.save(
                Dept.create(tenantId, 0L, tenant.getTenantName(), 0, null));

        // 2. 菜单模板：从平台租户复制，保持父子层级
        copyMenusFromPlatform(tenantId);

        // 3. 默认角色：租户管理员，授予除平台租户管理外的全部启用权限
        List<Long> permissionIds = permissionRepository.findEnabledAll().stream()
                .filter(permission -> !PLATFORM_ONLY_PERM_CODES.contains(permission.getPermCode().value()))
                .map(Permission::getId)
                .toList();
        Role adminRole = roleRepository.save(Role.create(tenantId, RoleCode.of(DEFAULT_TENANT_ADMIN_ROLE),
                "租户管理员", "租户开通时自动创建，管理本租户用户、角色、菜单与部门", 0, Role.STATUS_ENABLED));
        roleRepository.replacePermissions(adminRole.getId(), permissionIds);

        // 4. 初始管理员：归属根部门并绑定默认角色
        User admin = userRepository.save(User.create(tenantId, adminUsername,
                EncryptedPassword.of(passwordEncoder.encode(command.rawPassword())), command.adminNickname(),
                null, null, null, rootDept.getId(), User.STATUS_ENABLED));
        userRepository.replaceRoles(admin.getId(), List.of(adminRole.getId()));
        // 一人多租户：初始管理员的首页租户即新开通租户
        tenantMembershipAppService.ensureHomeMembership(admin.getId(), tenantId);
        tenantStatusCache.sync(tenant);

        log.info("租户开通完成: tenantId={}, tenantCode={}, adminUserId={}, menuCount={}",
                tenantId, tenantCode, admin.getId(), permissionIds.size());
        return TenantVO.from(tenant);
    }

    /**
     * 复制平台租户菜单到目标租户，按层级逐轮落库并建立新旧 ID 映射
     *
     * @param targetTenantId 目标租户 ID
     */
    private void copyMenusFromPlatform(Long targetTenantId) {
        List<Menu> remaining = new ArrayList<>(
                menuRepository.findAllByTenantId(Tenant.PLATFORM_TENANT_ID));
        Map<Long, Long> idMapping = new HashMap<>();
        while (!remaining.isEmpty()) {
            boolean progressed = false;
            Iterator<Menu> iterator = remaining.iterator();
            while (iterator.hasNext()) {
                Menu source = iterator.next();
                // 父节点尚未映射时 get() 返回 null，属于「本轮跳过、下一轮重试」的正常情形；
                // 必须保持三元两端同为 Long，避免与原始字面量 0L 混用触发自动拆箱 NPE
                Long mappedParentId = source.getParentId() == 0L
                        ? Long.valueOf(Menu.ROOT_PARENT_ID) : idMapping.get(source.getParentId());
                if (mappedParentId == null) {
                    continue;
                }
                Menu copied = menuRepository.save(Menu.create(targetTenantId, mappedParentId,
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
