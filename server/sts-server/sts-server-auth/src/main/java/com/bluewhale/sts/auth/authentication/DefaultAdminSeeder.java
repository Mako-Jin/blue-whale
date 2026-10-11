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
 * 默认管理员种子数据 —— 解决"第一个账号从哪来"的引导问题。
 *
 * <p>建租户、建用户这些接口本身要求已认证，存在先有鸡还是先有蛋。
 * 本类在应用启动后（{@link ApplicationRunner}）种下一套最小可用数据：
 * 默认租户 + 管理员账号 + 本切片已落地的权限资源 + 授予管理员的超级管理员角色。
 *
 * <p><b>只在 {@code bluewhale.auth.seed-admin.enabled=true} 时生效</b>
 * （由 {@code AuthAutoConfiguration} 用条件装配控制），默认关闭、仅 h2 开发 profile 打开。
 *
 * <p><b>幂等</b>：每样东西都先查后插，重复启动不会产生第二份。
 * 这一点比"启动时插一次"重要得多 —— 后者多启一次就多一个重名账号。
 */
public class DefaultAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminSeeder.class);

    private static final String ADMIN_ROLE_CODE = "SUPER_ADMIN";
    private static final String ADMIN_ROLE_NAME = "超级管理员";

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
        RoleEntity role = ensureAdminRole(tenant, resources);
        UserEntity admin = ensureAdminUser(tenant, seed);
        ensureRoleGranted(role, admin);

        log.warn("已启用默认管理员种子数据 | 用户名={} | 租户={} | **这是开发用配置，生产环境必须关闭并立即改密**",
                seed.getUsername(), seed.getTenantCode());
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

    private RoleEntity ensureAdminRole(TenantEntity tenant, List<ResourceEntity> resources) {
        RoleEntity role = roleMapper.selectOne(Wrappers.<RoleEntity>lambdaQuery()
                .eq(RoleEntity::getTenantId, tenant.getId())
                .eq(RoleEntity::getRoleCode, ADMIN_ROLE_CODE));
        if (role == null) {
            role = new RoleEntity();
            role.setTenantId(tenant.getId());
            role.setRoleCode(ADMIN_ROLE_CODE);
            role.setRoleName(ADMIN_ROLE_NAME);
            role.setRoleLevel(1);
            role.setCategory("SYSTEM");
            role.setIsDefault(0);
            role.setDataScope("ALL");
            role.setSortNo(0);
            role.setStatus(Status.ENABLED.wireName());
            roleMapper.insert(role);
        }

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
        return role;
    }

    private UserEntity ensureAdminUser(TenantEntity tenant, AuthProperties.SeedAdmin seed) {
        UserEntity existing = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getTenantId, tenant.getId())
                .eq(UserEntity::getUsername, seed.getUsername()));
        if (existing != null) {
            return existing;
        }
        long now = System.currentTimeMillis();
        UserEntity user = new UserEntity();
        user.setTenantId(tenant.getId());
        user.setUsername(seed.getUsername());
        user.setDisplayName(seed.getDisplayName());
        user.setPasswordHash(passwordHasher.hash(seed.getPassword()));
        user.setPasswordAlgorithm(passwordHasher.algorithmId());
        user.setPasswordUpdatedAt(now);
        user.setMustChangePassword(0);
        user.setStatus(Status.ENABLED.wireName());
        user.setFailedAttempts(0);
        user.setMfaEnabled(0);
        userMapper.insert(user);
        return user;
    }

    private void ensureRoleGranted(RoleEntity role, UserEntity user) {
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
