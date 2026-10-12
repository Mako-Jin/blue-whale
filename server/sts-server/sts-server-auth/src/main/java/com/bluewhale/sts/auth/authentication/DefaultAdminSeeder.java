package com.bluewhale.sts.auth.authentication;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.auth.common.AuthPermissions;
import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.auth.common.codec.PasswordHasher;
import com.bluewhale.sts.auth.common.enums.MemberType;
import com.bluewhale.sts.auth.common.enums.ResourceType;
import com.bluewhale.sts.auth.common.enums.Status;
import com.bluewhale.sts.auth.relation.RelRoleMemberEntity;
import com.bluewhale.sts.auth.relation.RelRoleMemberMapper;
import com.bluewhale.sts.auth.relation.RelRoleResourceEntity;
import com.bluewhale.sts.auth.relation.RelRoleResourceMapper;
import com.bluewhale.sts.auth.resource.ResourceEntity;
import com.bluewhale.sts.auth.resource.ResourceMapper;
import com.bluewhale.sts.auth.role.RoleEntity;
import com.bluewhale.sts.auth.role.RoleMapper;
import com.bluewhale.sts.auth.tenant.TenantEntity;
import com.bluewhale.sts.auth.tenant.TenantMapper;
import com.bluewhale.sts.auth.user.UserEntity;
import com.bluewhale.sts.auth.user.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.List;

/**
 * 权限域的引导种子数据 —— 解决"第一个账号从哪来"。
 *
 * <p>建租户、建用户这些接口本身要求已认证，存在先有鸡还是先有蛋。
 * 本类在应用启动后（{@link ApplicationRunner}）种下一套最小可用数据：
 * 默认租户 + 两类身份 + 本切片已落地的权限资源 + 授予身份的角色。
 *
 * <p><b>两类身份，而不是一类：</b>
 * <ul>
 *   <li>{@code admin}（超级管理员）—— 持有 {@link AuthPermissions#ALL} 全部权限，用来"把事做成"；</li>
 *   <li>{@code auditor}（审计员）—— **只有读权限**，用来"证明确实拦住了写操作"。</li>
 * </ul>
 * 只种管理员的话，"权限到底生效没有"是验证不了的：所有接口对唯一身份都放行，
 * 权限标识写错、资源漏配、前端把按钮显隐判反，症状都只是"看起来很正常"。
 * 第二个身份把这件事变成**可观测的差异**（列表看得见、新建按钮不出现、直接调写接口得 403）。
 *
 * <p><b>只在 h2（开发 / 演示）profile 打开</b>：由 {@code AuthAutoConfiguration} 用条件装配控制，
 * 两个开关分别是 {@code bluewhale.auth.seed-admin.enabled} 与 {@code bluewhale.auth.seed-auditor.enabled}。
 *
 * <p><b>幂等</b>：每样东西都先查后插，重复启动不会产生第二份。
 * 这一点比"启动时插一次"重要得多 —— 后者多启一次就多一个重名账号。
 */
public class DefaultAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminSeeder.class);

    private static final String ADMIN_ROLE_CODE = "SUPER_ADMIN";
    private static final String ADMIN_ROLE_NAME = "超级管理员";

    private static final String AUDITOR_ROLE_CODE = "AUDITOR";
    private static final String AUDITOR_ROLE_NAME = "审计员";

    /**
     * 审计员的全部权限：**只有读**。
     *
     * <p>往这里加写权限会让"按钮该不该出现"的演示失去区分度，因此加之前先想清楚用途。
     */
    private static final List<String> AUDITOR_PERMISSIONS =
            List.of(AuthPermissions.USER_READ, AuthPermissions.TENANT_READ);

    private final AuthProperties properties;
    private final PasswordHasher passwordHasher;
    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final ResourceMapper resourceMapper;
    private final RoleMapper roleMapper;
    private final RelRoleResourceMapper relRoleResourceMapper;
    private final RelRoleMemberMapper relRoleMemberMapper;

    public DefaultAdminSeeder(AuthProperties properties,
                              PasswordHasher passwordHasher,
                              TenantMapper tenantMapper,
                              UserMapper userMapper,
                              ResourceMapper resourceMapper,
                              RoleMapper roleMapper,
                              RelRoleResourceMapper relRoleResourceMapper,
                              RelRoleMemberMapper relRoleMemberMapper) {
        this.properties = properties;
        this.passwordHasher = passwordHasher;
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.resourceMapper = resourceMapper;
        this.roleMapper = roleMapper;
        this.relRoleResourceMapper = relRoleResourceMapper;
        this.relRoleMemberMapper = relRoleMemberMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.SeedAdmin seed = properties.getSeedAdmin();
        if (!seed.isEnabled()) {
            return;
        }

        TenantEntity tenant = ensureTenant(seed);
        List<ResourceEntity> resources = ensureResources();

        RoleEntity adminRole = ensureRole(tenant, ADMIN_ROLE_CODE, ADMIN_ROLE_NAME);
        grantResources(tenant, adminRole, resources);
        UserEntity admin = ensureUser(tenant, seed.getUsername(), seed.getPassword(), seed.getDisplayName());
        grantRole(adminRole, admin);

        log.warn("已启用默认管理员种子数据 | 用户名={} | 租户={} | **这是开发用配置，生产环境必须关闭并立即改密**",
                seed.getUsername(), seed.getTenantCode());

        AuthProperties.SeedAuditor auditor = properties.getSeedAuditor();
        if (!auditor.isEnabled()) {
            return;
        }

        // 审计员的权限是资源的子集，从已确保存在的资源里挑，不另查一遍库
        List<ResourceEntity> readable = resources.stream()
                .filter(resource -> AUDITOR_PERMISSIONS.contains(resource.getResourceCode()))
                .toList();
        RoleEntity auditorRole = ensureRole(tenant, AUDITOR_ROLE_CODE, AUDITOR_ROLE_NAME);
        grantResources(tenant, auditorRole, readable);
        UserEntity auditorUser = ensureUser(tenant, auditor.getUsername(), auditor.getPassword(),
                auditor.getDisplayName());
        grantRole(auditorRole, auditorUser);

        log.warn("已启用默认只读账号种子数据 | 用户名={} | 权限={} | **同样是开发用配置**",
                auditor.getUsername(), AUDITOR_PERMISSIONS);
    }

    private TenantEntity ensureTenant(AuthProperties.SeedAdmin seed) {
        TenantEntity existing = tenantMapper.selectOne(Wrappers.<TenantEntity>lambdaQuery()
                .eq(TenantEntity::getTenantCode, seed.getTenantCode()));
        if (existing != null) {
            return existing;
        }
        TenantEntity tenant = new TenantEntity();
        tenant.setTenantCode(seed.getTenantCode());
        tenant.setTenantName(seed.getTenantName());
        tenant.setTenantLevel(1);
        tenant.setCodePath("/");
        tenant.setHasChild(0);
        tenant.setStatus(Status.ENABLED.wireName());
        tenant.setAllowRegister(0);
        tenant.setAllowAdd(0);
        tenantMapper.insert(tenant);
        return tenant;
    }

    private List<ResourceEntity> ensureResources() {
        for (String code : AuthPermissions.ALL) {
            ResourceEntity existing = resourceMapper.selectOne(Wrappers.<ResourceEntity>lambdaQuery()
                    .eq(ResourceEntity::getResourceCode, code));
            if (existing != null) {
                continue;
            }
            ResourceEntity resource = new ResourceEntity();
            resource.setResourceCode(code);
            resource.setResourceName(code);
            resource.setResourceType(ResourceType.API.wireName());
            resource.setSortNo(0);
            resource.setIsEnabled(1);
            resource.setIsWhitelist(0);
            resource.setIsDeprecated(0);
            resource.setResourceVersion("1.0.0");
            resourceMapper.insert(resource);
        }
        return resourceMapper.selectList(Wrappers.<ResourceEntity>lambdaQuery()
                .in(ResourceEntity::getResourceCode, AuthPermissions.ALL));
    }

    private RoleEntity ensureRole(TenantEntity tenant, String roleCode, String roleName) {
        RoleEntity role = roleMapper.selectOne(Wrappers.<RoleEntity>lambdaQuery()
                .eq(RoleEntity::getTenantId, tenant.getId())
                .eq(RoleEntity::getRoleCode, roleCode));
        if (role != null) {
            return role;
        }
        role = new RoleEntity();
        role.setTenantId(tenant.getId());
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setRoleLevel(1);
        role.setCategory("SYSTEM");
        role.setIsDefault(0);
        role.setDataScope("ALL");
        role.setSortNo(0);
        role.setStatus(Status.ENABLED.wireName());
        roleMapper.insert(role);
        return role;
    }

    private void grantResources(TenantEntity tenant, RoleEntity role, List<ResourceEntity> resources) {
        for (ResourceEntity resource : resources) {
            Long count = relRoleResourceMapper.selectCount(Wrappers.<RelRoleResourceEntity>lambdaQuery()
                    .eq(RelRoleResourceEntity::getRoleId, role.getId())
                    .eq(RelRoleResourceEntity::getResourceId, resource.getId()));
            if (count != null && count > 0) {
                continue;
            }
            RelRoleResourceEntity grant = new RelRoleResourceEntity();
            grant.setTenantId(tenant.getId());
            grant.setRoleId(role.getId());
            grant.setResourceId(resource.getId());
            grant.setEffect("ALLOW");
            grant.setPriority(0);
            relRoleResourceMapper.insert(grant);
        }
    }

    private UserEntity ensureUser(TenantEntity tenant, String username, String rawPassword, String displayName) {
        UserEntity existing = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getTenantId, tenant.getId())
                .eq(UserEntity::getUsername, username));
        if (existing != null) {
            return existing;
        }
        long now = System.currentTimeMillis();
        UserEntity user = new UserEntity();
        user.setTenantId(tenant.getId());
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordHasher.hash(rawPassword));
        user.setPasswordAlgorithm(passwordHasher.algorithmId());
        user.setPasswordUpdatedAt(now);
        user.setMustChangePassword(0);
        user.setStatus(Status.ENABLED.wireName());
        user.setFailedAttempts(0);
        user.setMfaEnabled(0);
        userMapper.insert(user);
        return user;
    }

    private void grantRole(RoleEntity role, UserEntity user) {
        Long count = relRoleMemberMapper.selectCount(Wrappers.<RelRoleMemberEntity>lambdaQuery()
                .eq(RelRoleMemberEntity::getRoleId, role.getId())
                .eq(RelRoleMemberEntity::getMemberType, MemberType.USER.wireName())
                .eq(RelRoleMemberEntity::getMemberId, user.getId()));
        if (count != null && count > 0) {
            return;
        }
        RelRoleMemberEntity grant = new RelRoleMemberEntity();
        grant.setTenantId(role.getTenantId());
        grant.setRoleId(role.getId());
        grant.setMemberId(user.getId());
        grant.setMemberType(MemberType.USER.wireName());
        relRoleMemberMapper.insert(grant);
    }

}
