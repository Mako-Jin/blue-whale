package com.bluewhale.sts.auth.authentication;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.auth.common.AuthMenus;
import com.bluewhale.sts.auth.common.AuthPermissions;
import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.auth.common.codec.PasswordHasher;
import com.bluewhale.sts.auth.common.enums.MemberType;
import com.bluewhale.sts.auth.common.enums.ResourceType;
import com.bluewhale.sts.auth.common.enums.Status;
import com.bluewhale.sts.auth.organization.OrgMemberBindRequest;
import com.bluewhale.sts.auth.organization.OrganizationEntity;
import com.bluewhale.sts.auth.organization.OrganizationMapper;
import com.bluewhale.sts.auth.organization.OrganizationMemberService;
import com.bluewhale.sts.auth.organization.OrganizationSaveRequest;
import com.bluewhale.sts.auth.organization.OrganizationService;
import com.bluewhale.sts.auth.relation.RelOrgUserEntity;
import com.bluewhale.sts.auth.relation.RelOrgUserMapper;
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

    /**
     * 最小组织树：总部 → 研发部 → 前端组。
     *
     * <p><b>为什么要种组织。</b>{@code bw_auth_organization} 与
     * {@code bw_auth_rel_org_user} 从建库起就在，但全仓零引用 —— 也就是说
     * 在真实环境里这两张表**恒为空**。而 {@code DEPT} / {@code DEPT_AND_CHILD}
     * 两档数据范围求的就是"这个人的主岗在哪个组织"：表空着，这两档就永远
     * 算不出范围、只能保守地按"仅本人"处理。一棵最小三层树让它们**开箱可验**，
     * 也让"部门调整之后谁能看见什么"有一个可以指着看的例子。
     *
     * <p>三层的选择不是凑数：{@code DEPT} 与 {@code DEPT_AND_CHILD} 的差别要能被区分出来，
     * 就必须有一个节点**同时**有上级和下级（研发部），否则"本部门"与"本部门及下级"
     * 求出来的集合永远相等，两个档位看起来一模一样。
     */
    private static final String HQ_CODE = "HQ";
    private static final String HQ_NAME = "总部";
    private static final String RD_CODE = "RD";
    private static final String RD_NAME = "研发部";
    private static final String FE_CODE = "RD_FE";
    private static final String FE_NAME = "前端组";

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

    /**
     * 审计员的**菜单码** —— 与上面的权限清单刻意分成两份。
     *
     * <p><b>为什么分开：它们不是同类东西。</b>{@link #AUDITOR_PERMISSIONS} 是安全边界
     * （写进 {@code @RequiresPermission} 的判定），本清单只决定界面上出现哪些入口与区块
     * （{@code ResourceType.MENU} / {@code PAGE} 的 {@code requestScoped} 都是 {@code false}）。
     * 合成一个清单的后果是读代码的人会以为"撤销一条菜单码 = 收窄了一份权限" ——
     * 而它只会让某个入口消失，接口照样放行。两类事实分开写，才不会在需要判断
     * "这次改动动的是布局还是安全"时看错。
     *
     * <p><b>为什么是这十一项：</b>
     * <ul>
     *   <li>{@code ui:audit_console:view} + 它下面**三块都要** ——
     *       尤其 {@code ui:audit_drill:view}（演练区）。演练区是"让没权限的人点一下、
     *       看审计表里留下两条 FAILURE"的演示载体，它的按钮**刻意不做权限显隐**
     *       （见 {@code DrillPanel} 的注释）。把这一块从审计员那里关掉，
     *       等于把"越权尝试会被记下来"这件事从演示里抹掉 ——
     *       那正是这个账号存在的意义；</li>
     *   <li>{@code ui:admin:view} + 六个标签页全给 —— 于是它在权限管理里
     *       看得见六个标签页、其中四个点开是 {@code 40301}。
     *       **这是授权配置的结果，不是缺陷**：想让审计员只看用户与租户，
     *       撤掉那四条菜单码即可，不需要改任何代码。
     *       这恰好证明了菜单码是**独立可配的布局开关**。</li>
     * </ul>
     *
     * <p>它拿不到其它四个应用的任何菜单码，因此导航栏上只有两项 ——
     * 这不是"藏起来了"，而是那四个应用里它一个接口都调不通：
     * 给了入口只会得到一个点了就报错的页面（见 {@code MICRO_APPS} 的注释）。
     */
    private static final List<String> AUDITOR_MENUS = List.of(
            AuthPermissions.UI_AUDIT_CONSOLE_VIEW,
            AuthPermissions.UI_AUDIT_FLOW_VIEW,
            AuthPermissions.UI_AUDIT_STATS_VIEW,
            AuthPermissions.UI_AUDIT_DRILL_VIEW,
            AuthPermissions.UI_ADMIN_VIEW,
            AuthPermissions.UI_ADMIN_USER_VIEW,
            AuthPermissions.UI_ADMIN_TENANT_VIEW,
            AuthPermissions.UI_ADMIN_RESOURCE_VIEW,
            AuthPermissions.UI_ADMIN_ROLE_VIEW,
            AuthPermissions.UI_ADMIN_USER_GROUP_VIEW,
            AuthPermissions.UI_ADMIN_ORGANIZATION_VIEW);

    private final AuthProperties properties;
    private final PasswordHasher passwordHasher;
    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final ResourceMapper resourceMapper;
    private final RoleMapper roleMapper;
    private final RelRoleResourceMapper relRoleResourceMapper;
    private final RelRoleMemberMapper relRoleMemberMapper;
    private final OrganizationService organizationService;
    private final OrganizationMemberService organizationMemberService;
    private final OrganizationMapper organizationMapper;
    private final RelOrgUserMapper relOrgUserMapper;

    public DefaultAdminSeeder(AuthProperties properties,
                              PasswordHasher passwordHasher,
                              TenantMapper tenantMapper,
                              UserMapper userMapper,
                              ResourceMapper resourceMapper,
                              RoleMapper roleMapper,
                              RelRoleResourceMapper relRoleResourceMapper,
                              RelRoleMemberMapper relRoleMemberMapper,
                              OrganizationService organizationService,
                              OrganizationMemberService organizationMemberService,
                              OrganizationMapper organizationMapper,
                              RelOrgUserMapper relOrgUserMapper) {
        this.properties = properties;
        this.passwordHasher = passwordHasher;
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.resourceMapper = resourceMapper;
        this.roleMapper = roleMapper;
        this.relRoleResourceMapper = relRoleResourceMapper;
        this.relRoleMemberMapper = relRoleMemberMapper;
        this.organizationService = organizationService;
        this.organizationMemberService = organizationMemberService;
        this.organizationMapper = organizationMapper;
        this.relOrgUserMapper = relOrgUserMapper;
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
        // 组织树先建好：两个身份的主岗都挂在它上面（审计员在叶子节点上，
        // 于是"本部门"与"本部门及下级"在这套种子上能验出差别）
        OrganizationEntity hq = ensureOrg(tenant, HQ_CODE, HQ_NAME, null, "COMPANY");
        OrganizationEntity rd = ensureOrg(tenant, RD_CODE, RD_NAME, hq, "DEPT");
        OrganizationEntity fe = ensureOrg(tenant, FE_CODE, FE_NAME, rd, "TEAM");

        RoleEntity adminRole = ensureRole(tenant, ADMIN_ROLE_CODE, ADMIN_ROLE_NAME);
        grantResources(tenant, adminRole, resources);
        UserEntity admin = ensureUser(tenant, seed.getUsername(), seed.getPassword(), seed.getDisplayName());
        grantRole(adminRole, admin);
        ensureMembership(hq, admin);

        log.warn("已启用默认管理员种子数据 | 用户名={} | 租户={} | 主岗={} | "
                        + "**这是开发用配置，生产环境必须关闭并立即改密**",
                seed.getUsername(), seed.getTenantCode(), HQ_NAME);

        if (!auditor.isEnabled()) {
            return;
        }

        // 审计员的权限是资源的子集，从已确保存在的资源里挑，不另查一遍库。
        // 读权限与菜单码两份清单在这里汇合：**授权本身不区分这两类** ——
        // 它们都是"角色 → 资源"的一条 ALLOW 边，区别只在资源类型
        // （API 那条边会被 @RequiresPermission 读到，MENU/PAGE 那条边只被前端读到）。
        List<ResourceEntity> readable = resources.stream()
                .filter(resource -> AUDITOR_PERMISSIONS.contains(resource.getResourceCode())
                        || AUDITOR_MENUS.contains(resource.getResourceCode()))
                .toList();
        RoleEntity auditorRole = ensureRole(tenant, AUDITOR_ROLE_CODE, AUDITOR_ROLE_NAME);
        grantResources(tenant, auditorRole, readable);
        UserEntity auditorUser = ensureUser(tenant, auditor.getUsername(), auditor.getPassword(),
                auditor.getDisplayName());
        grantRole(auditorRole, auditorUser);
        ensureMembership(fe, auditorUser);

        log.warn("已启用默认只读账号种子数据 | 用户名={} | 读权限={} | 菜单码={} 项 | 主岗={} | **同样是开发用配置**",
                auditor.getUsername(), AUDITOR_PERMISSIONS, AUDITOR_MENUS.size(), FE_NAME);
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

    /**
     * 确保一个组织存在（幂等）。
     *
     * <p><b>这里刻意调用 {@link OrganizationService#create} 而不是自己拼 mapper 写入。</b>
     * 组织的 {@code orgLevel} / {@code codePath} / {@code fullName} 是**派生列**，
     * 种子里再实现一遍就等于把同一套规则抄了第二份 —— 而这份抄件不会有测试盯着，
     * 一旦派生规则改了（例如路径分隔符或层级的起点），种子会产生一批
     * 与接口建出来的节点**长得不一样**的数据，且只在重建库时才暴露。
     * 复用服务方法还有一个附带好处：编码被软删行占用时的复活逻辑也一并复用。
     *
     * <p>调用它不会踩到"启动时没有请求上下文"的坑：创建走的租户是**显式传入**的
     * （{@code resolveTenantId} 只在没传时才去读登录上下文），
     * 而 {@code OrganizationMemberService} 根本不读上下文。
     *
     * <p>先查一次是为了**幂等**：本服务方法在编码被一条有效组织占用时会抛
     * {@code 40908}，而种子的语义是"确保存在"，不是"每次都建"。
     */
    private OrganizationEntity ensureOrg(TenantEntity tenant, String code, String name,
                                         OrganizationEntity parent, String orgType) {
        OrganizationEntity existing = organizationMapper
                .selectAnyByTenantAndCode(tenant.getId(), code);
        if (existing != null && (existing.getDeleted() == null || existing.getDeleted() == 0)) {
            return existing;
        }
        OrganizationSaveRequest request = new OrganizationSaveRequest();
        request.setTenantId(tenant.getId());
        request.setOrgCode(code);
        request.setOrgName(name);
        request.setParentId(parent == null ? null : parent.getId());
        request.setOrgType(orgType);
        request.setSortNo(0);
        // 服务方法返回的是 VO；下一层节点要拿 parentId / codePath 派生子级，所以回读一次实体。
        // 只在启动时发生、一共三次，不值得为它给服务层开一个"返回实体"的口子
        // （那会让服务层的出参口径出现两种，而调用方要靠上下文猜自己拿到的是哪种）
        return organizationMapper.selectById(organizationService.create(request).getId());
    }

    /**
     * 确保某个用户在那个组织下**至少有一条归属**（幂等）。
     *
     * <p><b>只在这个人于该组织下没有任何归属时才写入。</b>也就是说：
     * 一旦运维在运行期改过这件事（把人调走、把主岗搬走），重启种子**不会**把它改回来。
     * 这与 {@code ensureUser} 的取舍不同（那个方法遇到已存在的用户直接返回、不改口令），
     * 但动机一致：种子负责"从无到有"，不负责"把状态纠正回它以为的样子"。
     *
     * <p>不传 {@code isPrimaryPosition}：由服务层按"此人当前没有主岗则设为主岗"决定。
     * 这样首次启动会自然形成"admin 主岗在总部、auditor 主岗在前端组"，
     * 而重启不会把运维改过的主岗悄悄搬回来 —— 搬主岗是直接影响数据范围的操作，
     * 只能由显式的接口调用发生。
     */
    private void ensureMembership(OrganizationEntity org, UserEntity user) {
        Long count = relOrgUserMapper.selectCount(Wrappers.<RelOrgUserEntity>lambdaQuery()
                .eq(RelOrgUserEntity::getOrganizationId, org.getId())
                .eq(RelOrgUserEntity::getUserId, user.getId()));
        if (count != null && count > 0) {
            return;
        }
        OrgMemberBindRequest request = new OrgMemberBindRequest();
        request.setMemberId(user.getId());
        request.setPositionName(org.getOrgName() + "成员");
        organizationMemberService.bindMember(org.getId(), request);
    }

    /**
     * 确保全部资源行存在（幂等），返回它们。
     *
     * <p><b>分两途建行，因为两类资源的形状不同：</b>
     * <ul>
     *   <li><b>接口资源</b>（{@link AuthPermissions#ALL} 里的 {@code auth:*} /
     *       {@code log:*} / {@code event:*}）—— 一码一行、无父级，类型 {@code API}。
     *       它们的名字就用编码本身：{@code @RequiresPermission} 里写的就是这个字符串，
     *       给它们编一个中文名反而会让"注解里的码"与"资源目录里的名字"开始分叉；</li>
     *   <li><b>菜单资源</b>（{@link AuthMenus}）—— 应用入口是 {@code MENU}、
     *       内部视图是 {@code PAGE}，页面用 {@code parentCode} 挂到应用下面。
     *       菜单**本来就是一棵树**，平铺进去只会得到一堆没有归属的孤行，
     *       而资源目录界面正是靠这个父子关系把它画成树。</li>
     * </ul>
     *
     * <p>两类资源的 {@code requestScoped} 都是"不参与后端准入"，但类型不能混：
     * {@code ResourceVO} 会把它派生出来给管理员看（"此权限不参与后端拦截"），
     * 类型写错就等于在后台显示了一句反话。
     *
     * <p><b>已有行一律不动。</b>包括类型与名字 —— 种子负责"从无到有"，
     * 不负责"把状态纠正回它以为的样子"（同 {@code ensureMembership} 的取舍）。
     * 开发库是内存 h2、每次重建，因此不存在"从旧版本升上来"的存量问题；
     * 真在别的环境开了种子又改了资源名，那也该是运维的决定，不是启动时的暗改。
     */
    private List<ResourceEntity> ensureResources() {
        List<String> menuCodes = AuthMenus.allCodes();

        for (String code : AuthPermissions.ALL) {
            if (menuCodes.contains(code)) {
                continue;
            }
            ensureResource(code, code, ResourceType.API, null, 0);
        }

        for (AuthMenus.Application application : AuthMenus.ALL) {
            ensureResource(application.code(), application.name(), ResourceType.MENU, null, 0);
            // 同一应用内按声明顺序排：它同时是导航与资源树的展示顺序，
            // 而两端顺序不一致时没有任何东西会报错 —— 只能靠这里保持"声明顺序即展示顺序"
            int sortNo = 1;
            for (AuthMenus.Page page : application.pages()) {
                ensureResource(page.code(), page.name(), ResourceType.PAGE, application.code(), sortNo++);
            }
        }

        return resourceMapper.selectList(Wrappers.<ResourceEntity>lambdaQuery()
                .in(ResourceEntity::getResourceCode, AuthPermissions.ALL));
    }

    private ResourceEntity ensureResource(String code, String name, ResourceType type,
                                          String parentCode, int sortNo) {
        ResourceEntity existing = resourceMapper.selectOne(Wrappers.<ResourceEntity>lambdaQuery()
                .eq(ResourceEntity::getResourceCode, code));
        if (existing != null) {
            return existing;
        }
        ResourceEntity resource = new ResourceEntity();
        resource.setResourceCode(code);
        resource.setResourceName(name);
        resource.setResourceType(type.wireName());
        resource.setParentCode(parentCode);
        resource.setSortNo(sortNo);
        resource.setIsEnabled(1);
        resource.setIsWhitelist(0);
        resource.setIsDeprecated(0);
        resource.setResourceVersion("1.0.0");
        resourceMapper.insert(resource);
        return resource;
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
