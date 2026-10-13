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
import org.springframework.util.StringUtils;

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

    /** 报错时要写进消息里的完整属性名 —— 手拼字符串最容易在消息里漏一段，所以做成常量。 */
    private static final String SEED_ADMIN_PROPERTY = "bluewhale.auth.seed-admin.password";
    private static final String SEED_AUDITOR_PROPERTY = "bluewhale.auth.seed-auditor.password";

    /**
     * 审计员的全部权限：**只有读**。
     *
     * <p>往这里加写权限会让"按钮该不该出现"的演示失去区分度，因此加之前先想清楚用途。
     *
     * <p>含 {@link AuthPermissions#AUDIT_READ} 是语义上必须的 —— "审计员"这个角色
     * 如果看不了审计流水，那这个名字就只是"只读账号"的别名。而它恰好还构成一个
     * 很强的演示：审计员**能读全部审计记录，却一条都改不了**，因为改动审计的接口
     * 在系统里根本不存在（见 {@code AuditLogController}）。"不可篡改"由接口缺席保证，
     * 而不是由权限配置是否配错来保证。
     */
    private static final List<String> AUDITOR_PERMISSIONS = List.of(
            AuthPermissions.USER_READ,
            AuthPermissions.TENANT_READ,
            AuthPermissions.AUDIT_READ);

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
        AuthProperties.SeedAuditor auditor = properties.getSeedAuditor();

        // 先校验、再落库：两个口令都齐了才开始写，避免"管理员种下了、审计员报错退出"的半成品状态。
        requirePassword(SEED_ADMIN_PROPERTY, seed.getUsername(), seed.getPassword());
        if (auditor.isEnabled()) {
            requirePassword(SEED_AUDITOR_PROPERTY, auditor.getUsername(), auditor.getPassword());
        }

        TenantEntity tenant = ensureTenant(seed);
        List<ResourceEntity> resources = ensureResources();

        RoleEntity adminRole = ensureRole(tenant, ADMIN_ROLE_CODE, ADMIN_ROLE_NAME);
        grantResources(tenant, adminRole, resources);
        UserEntity admin = ensureUser(tenant, seed.getUsername(), seed.getPassword(), seed.getDisplayName());
        grantRole(adminRole, admin);

        log.warn("已启用默认管理员种子数据 | 用户名={} | 租户={} | **这是开发用配置，生产环境必须关闭并立即改密**",
                seed.getUsername(), seed.getTenantCode());

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

    /**
     * 开了种子却没给口令 ⇒ **拒绝启动**。
     *
     * <p><b>为什么是启动失败，而不是"跳过种子"或"给个默认口令"：</b>
     * <ul>
     *   <li>跳过种子会安静地不种任何账号，而运维以为自己有一个 —— 症状推迟到第一次登录才出现，
     *       且现场已经离配置很远；</li>
     *   <li>给默认口令就是本切片要消灭的那件事本身：口令写在仓库里，人人可查。</li>
     * </ul>
     * 启动失败是唯一不会骗人的信号，而且它把错误**定位到了那一行配置**。
     *
     * <p>这条校验也顺手把"从默认口令时代升上来的部署"挡在了启动阶段 ——
     * 那些部署此前依赖默认值，现在必须显式给一次口令。这是有意的：
     * 静默沿用旧默认口令比启动失败危险得多。
     */
    private static void requirePassword(String propertyName, String username, String password) {
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException(String.format(
                    "已启用种子账号 %s，但没有提供口令（%s 为空）。种子口令**没有默认值** —— "
                            + "有默认值就意味着'开启种子但忘了覆盖'会静默种下一个口令公开可查的管理员。"
                            + "请显式提供，例如 --%s=<口令>；开发环境的取值见 application-h2.yml。",
                    username, propertyName, propertyName));
        }
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
