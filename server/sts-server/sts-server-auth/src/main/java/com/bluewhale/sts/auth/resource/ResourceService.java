package com.bluewhale.sts.auth.resource;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.auth.common.enums.ResourceType;
import com.bluewhale.sts.auth.relation.RelRoleResourceEntity;
import com.bluewhale.sts.auth.relation.RelRoleResourceMapper;
import com.bluewhale.sts.common.basic.model.PageResult;
import com.bluewhale.sts.common.db.query.FilterValue;
import com.bluewhale.sts.common.db.query.KeywordLike;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 资源目录应用服务。
 *
 * <p><b>它的性质与其它三个管理接口不同：它维护的是"权限本身"。</b>
 * 用户 / 租户 / 组织的 CRUD 改的是数据，这里改的是**判定依据** ——
 * {@code bw_auth_resource} 的每一行都是一个权限码，被 {@code @RequiresPermission}
 * 与角色的授权关系引用着。因此本类的约束比别处紧：
 *
 * <ul>
 *   <li><b>资源标识（即权限码）不可修改。</b>与租户编码同理，但后果更直接：
 *       改一个码等于让所有持有旧码的授权静默失效 —— 角色的授权记录还在，
 *       却再也匹配不上任何 {@code @RequiresPermission}。要改就"新建 + 迁移"。</li>
 *   <li><b>删除有三道拒绝。</b>有子资源、已被角色引用，各自拒绝；理由见 {@code delete}。</li>
 *   <li><b>父子关系不成环。</b>环在树接口上会一路递归/序列化打转，
 *       而这张表允许被直接改（种子数据就是直接写的），所以两端都要防。</li>
 * </ul>
 *
 * <p><b>资源是平台级的，不按租户隔离。</b>{@code tenant_id} 一律留空，与种子数据一致。
 * 这不是偷懒：{@code AuthorizationMapper} 那条判定 SQL 只按 {@code ro.tenant_id}
 * 限定角色，**没有**按 {@code r.tenant_id} 限定资源。若这里开始写租户，
 * 判定侧不加对应的条件，就会出现"资源登记给了租户 A，租户 B 也能被授予"的假隔离 ——
 * 那比不隔离更危险。要做租户级资源，得连同判定 SQL 一起改，那是一个独立决定。
 *
 * <p><b>本切片不做"扫描 Controller 自动登记资源"。</b>它是资源目录最终该有的形态
 * （{@code request_url} / {@code request_method} 两列就是为它准备的），但需要先解决
 * "一个权限码对应多个接口"的问题：{@code auth:user:read} 同时保护
 * {@code GET /api/auth/users} 与 {@code GET /api/auth/users/{id}}，
 * 而这两列是单值的 —— 列结构本身就表达不了这件事。
 */
@Service
public class ResourceService {

    private final ResourceMapper resourceMapper;
    private final RelRoleResourceMapper relRoleResourceMapper;

    public ResourceService(ResourceMapper resourceMapper, RelRoleResourceMapper relRoleResourceMapper) {
        this.resourceMapper = resourceMapper;
        this.relRoleResourceMapper = relRoleResourceMapper;
    }

    @Transactional(readOnly = true)
    public PageResult<ResourceVO> page(ResourceQuery query) {
        // 过滤值先取到局部变量再交给 eq(...)。
        //
        // **`eq(condition, column, value)` 的 value 参数是无条件求值的**：Java 在调用前
        // 就把三个实参算完，condition 只决定这条条件要不要拼进 SQL，救不了求值过程本身。
        // 所以写成 `.eq(x.getParentCode() != null, ..., x.getParentCode().trim())` 时，
        // parentCode 为 null 会当场 NPE，而那个 != null 判断看起来像是保护。
        // 本仓其它两处 query 恰好只传 getter 本身（不调用方法），所以一直没暴露。
        String typeFilter = FilterValue.enumValue(query.getResourceType(),
                ResourceType::parse, ResourceType::wireName);
        String parentFilter = FilterValue.text(query.getParentCode());
        // 关键词先规范化（吃掉两端空白）再转义，见 KeywordLike（decisions/0067 · 0068）。
        String pattern = KeywordLike.pattern(query.getKeyword());

        var wrapper = Wrappers.<ResourceEntity>lambdaQuery()
                .and(pattern != null, w -> w
                        .apply(KeywordLike.condition("resource_code"), pattern)
                        .or().apply(KeywordLike.condition("resource_name"), pattern))
                .eq(typeFilter != null, ResourceEntity::getResourceType, typeFilter)
                .eq(parentFilter != null, ResourceEntity::getParentCode, parentFilter)
                .eq(query.getIsEnabled() != null, ResourceEntity::getIsEnabled, query.getIsEnabled())
                // 与树的顺序一致：同级按 sortNo，同序按 id（保证分页稳定 —— 只按 sortNo 排时，
                // 多条 sortNo 相同的记录在不同页之间的相对顺序是数据库的自由，
                // 于是"翻页时某条记录出现两次或一次都不出现"）
                .orderByAsc(ResourceEntity::getSortNo)
                .orderByAsc(ResourceEntity::getId);

        Page<ResourceEntity> page = resourceMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        List<ResourceVO> records = page.getRecords().stream().map(ResourceService::toVO).toList();
        return PageResult.of(records, page.getTotal(), query.getPage(), query.getSize());
    }

    @Transactional(readOnly = true)
    public ResourceVO detail(Long id) {
        return toVO(requireResource(id));
    }

    /**
     * 组装资源树。
     *
     * <p><b>一次全量查出、内存组装，不分页。</b>资源目录的量级是"几十到几百条"
     * （一个权限码一行，不是业务数据），全量查出的代价远低于在树上做递归 SQL；
     * 而分页在这里本身没有意义 —— 树被截断了就不再是树。
     *
     * <p><b>两个必须防的坑，都会导致整棵树渲染不出来：</b>
     * <ol>
     *   <li><b>孤儿节点</b>：{@code parent_code} 指向一个不存在（或已被删除）的标识。
     *       这类节点必须**当成根返回**，不能因为"找不到爸爸"就丢掉 ——
     *       否则父资源一被删，整棵子树就从界面上静默消失，而它们还在库里、还在产生权限。</li>
     *   <li><b>成环</b>：{@code a→b→a}。表允许被直接改（种子数据就是直接写库的），
     *       所以不能假设数据无环。环会让对象图自己指向自己，序列化时递归不到底。
     *       判定方式是"从候选父节点向上走，若走回自己或走不出环，就按根处理"。</li>
     * </ol>
     * 两者都按"退化成根的平铺"处理，而不是抛错：**看得到但层级不对，好过整棵树打不开**。
     */
    @Transactional(readOnly = true)
    public List<ResourceTreeNode> tree() {
        List<ResourceEntity> all = resourceMapper.selectList(Wrappers.<ResourceEntity>lambdaQuery()
                .orderByAsc(ResourceEntity::getSortNo)
                .orderByAsc(ResourceEntity::getId));

        Map<String, ResourceTreeNode> byCode = new LinkedHashMap<>();
        for (ResourceEntity entity : all) {
            byCode.put(entity.getResourceCode(), toTreeNode(entity));
        }

        List<ResourceTreeNode> roots = new ArrayList<>();
        for (ResourceTreeNode node : byCode.values()) {
            String parentCode = node.getParentCode();
            if (parentCode == null || !canAttach(node.getResourceCode(), parentCode, byCode)) {
                roots.add(node);
                continue;
            }
            byCode.get(parentCode).getChildren().add(node);
        }
        return roots;
    }

    @Transactional
    public ResourceVO create(ResourceSaveRequest request) {
        String resourceCode = request.getResourceCode().trim();
        String resourceType = resolveResourceType(request.getResourceType());
        String parentCode = FilterValue.text(request.getParentCode());
        assertParentExists(parentCode);

        ResourceEntity existing = resourceMapper.selectAnyByCode(resourceCode);
        boolean occupied = existing != null && (existing.getDeleted() == null || existing.getDeleted() == 0);
        if (occupied) {
            throw AuthResultCode.RESOURCE_CODE_EXISTS.businessException();
        }

        ResourceEntity entity;
        if (existing != null) {
            // 这个标识曾被逻辑删除 —— **复活那一行**，而不是插一条新的。
            //
            // 不得不这么做，是因为唯一索引建在 resource_code 单列上（不含 deleted）：
            // 软删的行仍然占着这个标识，直接 insert 会撞键，而那条 DuplicateKeyException
            // 会被兜底成 40900「数据已存在或与已有记录冲突」（ADR-0024 之前是 500）；
            // 对外那句话里没有主语，实际原因只是"你删过它"。
            //
            // 复活在语义上也更对：create_time 保留了这个标识**第一次**出现的时间，
            // 而"删过又加回来"这件事在审计里本就是两条记录（delete + create），历史是完整的。
            resourceMapper.restoreDeletedById(existing.getId());
            entity = existing;
            // 内存里的 deleted 也必须清掉：MyBatis-Plus 的 updateById 把非空字段写进 SET，
            // 留着 1 就会把刚恢复的那行当场再删一次（而且影响 0 行、不报错）。
            entity.setDeleted(0);
        } else {
            entity = new ResourceEntity();
            entity.setResourceCode(resourceCode);
        }

        applyTo(entity, request, resourceType, parentCode);
        if (existing == null) {
            resourceMapper.insert(entity);
        } else {
            resourceMapper.updateById(entity);
        }
        return toVO(entity);
    }

    @Transactional
    public ResourceVO update(Long id, ResourceSaveRequest request) {
        ResourceEntity entity = requireResource(id);
        String resourceType = resolveResourceType(request.getResourceType());
        String parentCode = FilterValue.text(request.getParentCode());

        if (parentCode != null) {
            if (parentCode.equals(entity.getResourceCode())) {
                throw AuthResultCode.HIERARCHY_CYCLE.businessException("不能把资源挂到自己下面");
            }
            assertParentExists(parentCode);
            if (isDescendant(entity.getResourceCode(), parentCode)) {
                throw AuthResultCode.HIERARCHY_CYCLE.businessException(
                        "不能把资源挂到自己的下级下面：" + parentCode);
            }
        }

        // resourceCode 不在修改项内 —— 见类注释。这里刻意不读 request.getResourceCode()：
        // 它在 DTO 上是 @NotBlank（创建时要必填），若在 update 里"顺手"写进去，
        // 就成了一个能改权限码的接口，而它造成的失效是静默的。
        applyTo(entity, request, resourceType, parentCode);
        resourceMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * 删除资源（逻辑删除）。
     *
     * <p>两道拒绝，都不是形式主义：
     *
     * <ol>
     *   <li><b>有子资源时拒绝。</b>与租户一致：不提供藏起来的级联。删掉父节点会让
     *       子资源变成孤儿（树接口会兜住，但层级信息已经丢了）。</li>
     *   <li><b>已被角色引用时拒绝。</b>这是本类最要紧的一条。资源是逻辑删除，
     *       删掉之后 {@code AuthorizationMapper} 的 JOIN 会自动把它排除 ——
     *       于是一次删除会让若干角色**静默地**少掉一项权限。管理员的本意可能只是
     *       "清理一条不再用的资源"，实际效果却是"某些人突然点不动某个按钮了"，
     *       而且没有任何界面会提示这件事发生过。要求先显式撤销授权（那时能看到
     *       受影响的是哪些角色），再删资源，代价是两步，换来每一步都可解释。</li>
     * </ol>
     *
     * <p>拒绝时用 409 而不是 400：请求本身完全合法，是当前状态不允许。
     */
    @Transactional
    public void delete(Long id) {
        ResourceEntity entity = requireResource(id);

        Long childCount = resourceMapper.selectCount(Wrappers.<ResourceEntity>lambdaQuery()
                .eq(ResourceEntity::getParentCode, entity.getResourceCode()));
        if (childCount != null && childCount > 0) {
            throw AuthResultCode.HIERARCHY_CYCLE.businessException(
                    "该资源下仍有 " + childCount + " 个子资源，请先处理子资源");
        }

        Long grantCount = relRoleResourceMapper.selectCount(Wrappers.<RelRoleResourceEntity>lambdaQuery()
                .eq(RelRoleResourceEntity::getResourceId, entity.getId()));
        if (grantCount != null && grantCount > 0) {
            throw AuthResultCode.RESOURCE_IN_USE.businessException(
                    "该资源已被 " + grantCount + " 个角色引用，请先撤销授权");
        }

        resourceMapper.deleteById(entity.getId());
    }

    /* ------------------------------------------------------------------ 取数与校验 */

    /**
     * 取一个资源；**它不做租户判定，这是刻意的**（{@code decisions/0076}）。
     *
     * <p>{@code bw_auth_resource} 是**平台级**表：{@code tenant_id} 允许为空、本仓恒为空
     * （只有顶级租户的成员能维护资源目录 —— 见本类接口上的权限码）。
     * 对它的实体调 {@code TenantService#assertVisible} 是**空操作**（那条判定遇到
     * {@code null} 直接返回），所以这里不加那句话不是缺口，只是不写一句不做事的代码。
     *
     * <p>这条理由由 {@code scripts/verify-tenant-visibility.mjs} 从建表脚本里**读出来**：
     * 它是那条守卫推导出的两处例外之一（另一处是 {@code TenantService#requireTenant} ——
     * {@code bw_auth_tenant} 根本没有 {@code tenant_id} 列，它本身是租户）。
     * 守卫的射程是推导的而不是列出来的，所以把这一列改成 {@code NOT NULL} 之后，
     * 它会立刻要求这里补上判定（扰动验过）。
     */
    private ResourceEntity requireResource(Long id) {
        ResourceEntity entity = id == null ? null : resourceMapper.selectById(id);
        if (entity == null) {
            throw AuthResultCode.RESOURCE_NOT_FOUND.businessException();
        }
        return entity;
    }

    private void assertParentExists(String parentCode) {
        if (parentCode == null) {
            return;
        }
        Long count = resourceMapper.selectCount(Wrappers.<ResourceEntity>lambdaQuery()
                .eq(ResourceEntity::getResourceCode, parentCode));
        if (count == null || count == 0) {
            throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException("父资源不存在：" + parentCode);
        }
    }

    /**
     * 从 {@code candidateCode} 沿 parent_code 向上走，看是否会遇到 {@code ancestorCode}。
     *
     * <p>逐级一次查询：树深通常 3~5 层，比写一条递归 CTE 更简单，
     * 也不依赖三套数据库各自不同的递归语法（达梦与 MySQL 的写法并不一样）。
     *
     * <p>{@code seen} 是**终止保证**：万一库里已经存在环（表可被直接改），
     * 没有它这里就会转不出来。遇到重复节点即返回 false —— 环的兜底交给
     * {@link #canAttach}，那里是"渲染前"的最后一道。
     */
    private boolean isDescendant(String ancestorCode, String candidateCode) {
        Set<String> seen = new HashSet<>();
        String cursor = candidateCode;
        while (cursor != null) {
            if (cursor.equals(ancestorCode)) {
                return true;
            }
            if (!seen.add(cursor)) {
                return false;
            }
            ResourceEntity node = resourceMapper.selectOne(Wrappers.<ResourceEntity>lambdaQuery()
                    .eq(ResourceEntity::getResourceCode, cursor));
            cursor = node == null ? null : node.getParentCode();
        }
        return false;
    }

    /**
     * 把 {@code node} 挂到 {@code parentCode} 下是否安全。
     *
     * <p>返回 false 的三种情形，都会退化成"按根返回"：
     * <ol>
     *   <li><b>父不存在</b>（孤儿：父被删了，或标识本来就没登记）—— 必须先判这一条，
     *       否则后面 {@code byCode.get(parentCode)} 会拿到 null 而在拼树时 NPE。
     *       判定与取值用同一个 Map，所以这里查过存在性之后，调用方那次 get 必定非空。</li>
     *   <li><b>指向自己</b>。</li>
     *   <li><b>向上走会遇到自己、或走不出环</b>（数据里已有 a→b→a）。</li>
     * </ol>
     */
    private static boolean canAttach(String selfCode, String parentCode, Map<String, ResourceTreeNode> byCode) {
        if (!byCode.containsKey(parentCode)) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        String cursor = parentCode;
        while (cursor != null) {
            if (cursor.equals(selfCode)) {
                return false;
            }
            if (!seen.add(cursor)) {
                return false;
            }
            ResourceTreeNode node = byCode.get(cursor);
            cursor = node == null ? null : node.getParentCode();
        }
        return true;
    }

    /* ------------------------------------------------------------------ 字段组装 */

    private static void applyTo(ResourceEntity entity, ResourceSaveRequest request,
                                String resourceType, String parentCode) {
        entity.setResourceName(request.getResourceName().trim());
        entity.setResourceType(resourceType);
        entity.setResourceCategory(FilterValue.text(request.getResourceCategory()));
        entity.setParentCode(parentCode);
        entity.setRequestUrl(FilterValue.text(request.getRequestUrl()));
        entity.setRequestMethod(normalizeMethod(request.getRequestMethod()));
        entity.setIcon(FilterValue.text(request.getIcon()));
        entity.setSortNo(orDefault(request.getSortNo(), 0));
        entity.setIsEnabled(flag(request.getIsEnabled(), 1, "isEnabled"));
        entity.setIsWhitelist(flag(request.getIsWhitelist(), 0, "isWhitelist"));
        entity.setIsDeprecated(flag(request.getIsDeprecated(), 0, "isDeprecated"));
        entity.setResourceVersion(FilterValue.text(request.getResourceVersion()));
        entity.setDescription(FilterValue.text(request.getDescription()));
    }

    private static ResourceVO toVO(ResourceEntity entity) {
        ResourceVO vo = new ResourceVO();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setResourceCode(entity.getResourceCode());
        vo.setResourceName(entity.getResourceName());
        vo.setResourceType(entity.getResourceType());
        vo.setRequestScoped(requestScopedOf(entity.getResourceType()));
        vo.setResourceCategory(entity.getResourceCategory());
        vo.setParentCode(entity.getParentCode());
        vo.setRequestUrl(entity.getRequestUrl());
        vo.setRequestMethod(entity.getRequestMethod());
        vo.setIcon(entity.getIcon());
        vo.setSortNo(entity.getSortNo());
        vo.setIsEnabled(entity.getIsEnabled());
        vo.setIsWhitelist(entity.getIsWhitelist());
        vo.setIsDeprecated(entity.getIsDeprecated());
        vo.setResourceVersion(entity.getResourceVersion());
        vo.setDescription(entity.getDescription());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private static ResourceTreeNode toTreeNode(ResourceEntity entity) {
        ResourceTreeNode node = new ResourceTreeNode();
        node.setId(entity.getId());
        node.setResourceCode(entity.getResourceCode());
        node.setResourceName(entity.getResourceName());
        node.setResourceType(entity.getResourceType());
        node.setRequestScoped(requestScopedOf(entity.getResourceType()));
        node.setParentCode(entity.getParentCode());
        node.setIcon(entity.getIcon());
        node.setSortNo(entity.getSortNo());
        node.setIsEnabled(entity.getIsEnabled());
        return node;
    }

    /** 由资源类型推导"是否参与后端准入" —— 派生字段，不接受入参 */
    private static Boolean requestScopedOf(String resourceType) {
        ResourceType parsed = ResourceType.parse(resourceType);
        return parsed == null ? null : parsed.isRequestScoped();
    }

    /**
     * 写入时严格：类型不合法就报错，并把合法取值列进文案 ——
     * 否则管理员只会看到"保存成功"，然后在若干天后发现某条权限不生效。
     */
    private static String resolveResourceType(String raw) {
        ResourceType parsed = ResourceType.parse(raw);
        if (parsed == null) {
            throw AuthResultCode.PARAM_INVALID.businessException(
                    "资源类型不合法，取值：" + Arrays.stream(ResourceType.values())
                            .map(ResourceType::wireName)
                            .collect(Collectors.joining(" / ")));
        }
        return parsed.wireName();
    }

    /**
     * HTTP 方法：先与别处同一份规范化（吃两端空白、空白转 {@code null}），再往大写归一 ——
     * 这一列存的是大写。后半句是该列的**比较语义**（与日志域 {@code level} 存小写反向同源），
     * 不是通用规范化，所以留在这里而不是下沉进 {@code FilterValue}。
     */
    private static String normalizeMethod(String value) {
        String normalized = FilterValue.text(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static Integer orDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    /**
     * 三态标志位只接受 0 / 1。
     *
     * <p>放行别的值不会报错，只会静默地改变语义：判定 SQL 里是 {@code r.is_enabled = 1}，
     * 于是 {@code is_enabled = 7} 的资源"看起来启用了，实际不产生权限" ——
     * 而库里那一行读起来完全正常。这类值只能从接口写进来，因此在这里挡住最省事。
     */
    private static Integer flag(Integer value, int defaultValue, String fieldName) {
        if (value == null) {
            return defaultValue;
        }
        if (value != 0 && value != 1) {
            throw AuthResultCode.PARAM_INVALID.businessException(
                    fieldName + " 只能是 0 或 1，收到：" + value);
        }
        return value;
    }

}
