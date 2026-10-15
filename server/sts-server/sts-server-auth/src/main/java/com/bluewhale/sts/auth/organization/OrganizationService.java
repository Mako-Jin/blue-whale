package com.bluewhale.sts.auth.organization;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.auth.common.enums.Status;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import com.bluewhale.sts.auth.relation.RelOrgUserEntity;
import com.bluewhale.sts.auth.relation.RelOrgUserMapper;
import com.bluewhale.sts.auth.tenant.TenantService;
import com.bluewhale.sts.auth.user.UserEntity;
import com.bluewhale.sts.auth.user.UserMapper;
import com.bluewhale.sts.common.basic.model.PageResult;
import com.bluewhale.sts.common.db.query.FilterValue;
import com.bluewhale.sts.common.db.query.KeywordLike;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织应用服务 —— 组织树（部门树）的增删改查与**移动**。
 *
 * <p><b>这一支补齐的是数据权限的坐标系。</b>在它落地之前，
 * {@code DataScope} 的 {@code DEPT} / {@code DEPT_AND_CHILD} 两档是**算不出**的
 * （{@code precise = false}），任何查询只能把它们保守地当成"仅本人"；
 * 而 {@code OrganizationEntity} 与 {@code OrganizationMapper} 虽然从建库起就在，
 * 却**全仓零引用** —— 没有接口、没有种子，两张表在真实环境里恒为空。
 * 也就是说"配了一个本部门范围的角色"此前只能得到一个更小的范围，且无处可疑。
 *
 * <p><b>三列派生字段一起维护，这是本类的核心不变式：</b>
 * <pre>
 *   org_level   根为 1，子 = 父 + 1
 *   code_path   形如 /1/5/ —— 各级**祖先**的主键加斜杠，不含自身；根为 /
 *   full_name   形如 蓝鲸集团/研发部/前端组 —— 各级名称按树拼出来
 * </pre>
 * 三者都是 {@code parentId} 与各节点名称的**确定函数**，因此一律不接受调用方指定，
 * 并在建、改名、移动时由 {@link #rewriteSubtree} 统一重算。理由不是洁癖：
 * {@code DEPT_AND_CHILD} 档位正是按 {@code code_path} 做前缀匹配求子树
 * （见 {@code DataScopeService}），一份与 {@code parent_id} 不一致的路径会让
 * 子树上的人**静默地**算不进任何人的范围 —— 没有任何报错、界面上也看不出异常。
 *
 * <p><b>与 {@code TenantService} 的两点差异，都是刻意的：</b>
 * <ol>
 *   <li><b>支持移动节点。</b>租户树没有移动，因为租户层级是"公司的组织架构"，
 *       极少变；而部门调整是常态。移动的代价是必须重写整棵子树，见 {@link #move}；</li>
 *   <li><b>状态非法就报错，不静默兜底。</b>租户的 {@code resolveStatus} 把无法识别的值
 *       当成 {@code ENABLED}；组织不能这么做 —— 它的状态是**真开关**
 *       （停用的组织不再能作为 DEPT 档位的范围目标），把 {@code DISABLE} 这个拼写
 *       悄悄存成"启用"会直接改变谁看得见什么。</li>
 * </ol>
 *
 * <p><b>{@code has_child} 的重算已不在差异之内</b>（此前是第 2 条）：租户侧原先只做
 * 0→1、反向不重算，理由是"租户树没有移动"，但删除在租户侧是存在的、而删除正是唯一
 * 能把它从 1 变 0 的动作 —— 理由不覆盖事实。两侧现已同形，见
 * {@code TenantService#refreshHasChild}（ADR-0053）。
 *
 * <p><b>停用不级联。</b>停用一个组织不会影响它的下级 —— 与"停用角色只停用该角色"、
 * "停用用户组只停用该组"完全一致。它自己的含义是明确的：不再能作为范围目标、
 * 也不再能作为别人的父级被引用（若已停用，新建下级会被拒绝，见 {@link #resolveParent}）。
 */
@Service
public class OrganizationService {

    private final OrganizationMapper organizationMapper;
    private final RelOrgUserMapper relOrgUserMapper;
    private final UserMapper userMapper;
    private final TenantService tenantService;

    public OrganizationService(OrganizationMapper organizationMapper,
                               RelOrgUserMapper relOrgUserMapper,
                               UserMapper userMapper,
                               TenantService tenantService) {
        this.organizationMapper = organizationMapper;
        this.relOrgUserMapper = relOrgUserMapper;
        this.userMapper = userMapper;
        this.tenantService = tenantService;
    }

    /* ------------------------------------------------------------------ 查询 */

    @Transactional(readOnly = true)
    public PageResult<OrganizationVO> page(OrganizationQuery query) {
        // 关键词先规范化（吃掉两端空白）再转义，见 KeywordLike（decisions/0067 · 0068）。
        String pattern = KeywordLike.pattern(query.getKeyword());
        // 过滤值先取局部变量：`eq(条件, 列, 值)` 的"值"是**无条件求值**的，
        // 写成 `query.getStatus().trim()` 会在 status 为空时当场 NPE，
        // 外层的条件参数救不了。这条坑在本仓已经踩过三次。
        // status 是枚举列 ⇒ 走 enumValue（decisions/0070），与用户 / 租户 / 角色三域同一条口径；
        // orgType 是**自由文本**（只作展示、写入时不校验取值）⇒ 只 trim，见 FilterValue 类注释。
        String status = FilterValue.enumValue(query.getStatus(), Status::parse, Status::wireName);
        String orgType = FilterValue.text(query.getOrgType());
        Long tenantId = resolveTenantId(query.getTenantId());

        var wrapper = Wrappers.<OrganizationEntity>lambdaQuery()
                .eq(OrganizationEntity::getTenantId, tenantId)
                .and(pattern != null, w -> w
                        .apply(KeywordLike.condition("org_code"), pattern)
                        .or().apply(KeywordLike.condition("org_name"), pattern)
                        .or().apply(KeywordLike.condition("full_name"), pattern))
                .eq(status != null, OrganizationEntity::getStatus, status)
                .eq(orgType != null, OrganizationEntity::getOrgType, orgType)
                .eq(query.getParentId() != null, OrganizationEntity::getParentId, query.getParentId())
                // 先 orgLevel 再 sortNo 再 id：先给"按层级看"一个稳定的顺序，
                // 同级内按人工排序号；只按 sortNo 排时同序记录在不同页之间的相对顺序
                // 由数据库决定，"翻页时某条记录出现两次或一次都不出现"就是这么来的
                .orderByAsc(OrganizationEntity::getOrgLevel)
                .orderByAsc(OrganizationEntity::getSortNo)
                .orderByAsc(OrganizationEntity::getId);

        Page<OrganizationEntity> page =
                organizationMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        List<OrganizationVO> records = page.getRecords().stream().map(OrganizationService::toVO).toList();
        return PageResult.of(records, page.getTotal(), query.getPage(), query.getSize());
    }

    /**
     * 组织树。一次返回当前租户的全部节点（含已停用的，见下）。
     *
     * <p><b>一次查全量、在内存里拼树，不做递归查询。</b>部门树是人手维护的量级
     * （几十到几百个节点），一次全取比"每层一次查询"更快也更好读；
     * 而递归 CTE 在三方言里的写法差别不小（达梦要用 {@code CONNECT BY} 或
     * {@code WITH RECURSIVE}，且 {@code VARCHAR(n CHAR)} 会影响拼接），
     * 不值得为几百行数据引入一个跨方言的语法分支。
     *
     * <p><b>已停用的节点也返回</b>，这与其他查询一律按状态过滤不同 ——
     * 树是**管理界面**，停用的部门仍然要在树上可见（否则它连同整棵子树一起消失，
     * 表现为"我的部门不见了"，而真实原因是它被停用了）。节点上带着 {@code status}，
     * 由界面决定怎么置灰。
     *
     * <p><b>父节点不在结果集里的节点会被当成根返回</b>（孤儿节点）。
     * 正常写入路径不会产生孤儿，但表可以被直接改；把孤儿过滤掉等于让整棵子树
     * 在界面上凭空消失，而那是最难排查的一类"数据看起来少了"。
     *
     * @param rootId 只看某一棵子树时传它的主键；不传返回整个租户的森林
     */
    @Transactional(readOnly = true)
    public List<OrganizationTreeNode> tree(Long rootId) {
        Long tenantId = resolveTenantId(null);
        List<OrganizationEntity> all = organizationMapper.selectList(
                Wrappers.<OrganizationEntity>lambdaQuery()
                        .eq(OrganizationEntity::getTenantId, tenantId)
                        .orderByAsc(OrganizationEntity::getOrgLevel)
                        .orderByAsc(OrganizationEntity::getSortNo)
                        .orderByAsc(OrganizationEntity::getId));

        Map<Long, OrganizationTreeNode> nodes = new LinkedHashMap<>();
        for (OrganizationEntity entity : all) {
            nodes.put(entity.getId(), toTreeNode(entity));
        }

        List<OrganizationTreeNode> roots = new ArrayList<>();
        for (OrganizationEntity entity : all) {
            OrganizationTreeNode node = nodes.get(entity.getId());
            OrganizationTreeNode parent = entity.getParentId() == null
                    ? null : nodes.get(entity.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }

        if (rootId == null) {
            return roots;
        }
        OrganizationTreeNode root = nodes.get(rootId);
        if (root == null) {
            throw AuthResultCode.ORGANIZATION_NOT_FOUND.businessException();
        }
        return List.of(root);
    }

    @Transactional(readOnly = true)
    public OrganizationVO detail(Long id) {
        return toVO(requireOrg(id));
    }

    /* ------------------------------------------------------------------ 写入 */

    /**
     * 新建组织。
     *
     * <p><b>编码被一条已删除的组织占着时会复活那一行</b>，与
     * {@code UserGroupService.create} / {@code RoleService.create} 是同一支：
     * 唯一索引 {@code uk_bw_auth_org_tenant_code} 建在 {@code (tenant_id, org_code)} 上、
     * **不含 {@code deleted}**，而 MyBatis-Plus 给所有自动查询追加 {@code deleted = 0} ——
     * 常规判重看不见软删行，判重通过后 insert 撞唯一键，最终落到兜底的
     * {@code 40900}「数据已存在或与已有记录冲突」（{@code ADR-0024} 之前是 500）。
     *
     * <p>复活时清掉**这个组织名下的用户归属**，让"复活 = 一个没有人的空部门"成为
     * 无条件成立的语义。不清的后果很具体：一批人**静默地重新拿到**这个部门带来的
     * 数据范围（他们的 {@code DEPT} 主岗指向的就是这个 id），而调用方
     * 以为自己只是新建了一个同名部门。
     *
     * <p><b>组织不持有角色，所以没有第二个"一并清掉"的动作</b> ——
     * 角色挂的是用户与用户组，不挂组织。这一点与用户组不同，值得写下来，
     * 免得后来者照着那边去找一个不存在的级联。
     *
     * <p>{@code org_name} **不做唯一性校验**：不同上级下同名是合理的
     * （两个分公司各有一个"综合部"），表上也没有对应的唯一索引。
     * 详见 {@code AuthResultCode.ORG_NAME_EXISTS}。
     */
    @Transactional
    public OrganizationVO create(OrganizationSaveRequest request) {
        String orgCode = request.getOrgCode().trim();
        Long tenantId = resolveTenantId(request.getTenantId());
        // 与 resolveParent 里"停用的父组织不许挂新下级"同一条，只是范围更大：
        // 停用的租户连登录都进不去，它下面的组织更不可能被谁当成范围目标。见 ADR-0051
        tenantService.assertAcceptsNewData(tenantId);
        OrganizationEntity parent = resolveParent(request.getParentId(), tenantId);

        OrganizationEntity existing = organizationMapper.selectAnyByTenantAndCode(tenantId, orgCode);
        boolean occupied = existing != null && (existing.getDeleted() == null || existing.getDeleted() == 0);
        if (occupied) {
            throw AuthResultCode.ORG_CODE_EXISTS.businessException();
        }

        OrganizationEntity entity;
        Long staleParentId = null;
        if (existing != null) {
            // 复活分支：记下它原来的父级，后面要把那个父级的 hasChild 一起重算
            staleParentId = existing.getParentId();
            organizationMapper.restoreDeletedById(existing.getId());
            // 内存里的 deleted 也要清掉：update 的 WHERE 带 deleted = 0，留着 1 会静默影响 0 行
            entity = existing;
            entity.setDeleted(0);
            clearMembersOf(entity.getId());
        } else {
            entity = new OrganizationEntity();
            entity.setOrgCode(orgCode);
            entity.setTenantId(tenantId);
        }

        entity.setParentId(parent == null ? null : parent.getId());
        entity.setOrgLevel(levelOf(parent));
        entity.setCodePath(pathOf(parent));
        entity.setFullName(namePathOf(parent, request.getOrgName().trim()));
        applyCommon(entity, request);
        entity.setHasChild(0);

        if (existing == null) {
            organizationMapper.insert(entity);
        } else {
            updateColumns(entity);
        }

        // 两边的 hasChild 都要重算：新父级从 0 变 1，原父级可能从 1 变 0
        refreshHasChild(entity.getParentId());
        refreshHasChild(staleParentId);
        return toVO(entity);
    }

    /**
     * 修改组织资料。**不碰层级、不碰编码、不碰所属租户。**
     *
     * <p>{@code orgCode} 与 {@code tenantId} 不在修改项内，理由与用户组相同：
     * 前者是对外稳定标识，后者决定这棵树属于谁 —— 把组织"搬到"另一个租户会让它
     * 连同整棵子树瞬间与所有成员归属错位。
     *
     * <p><b>层级变更被显式拒绝（40001），而不是静默忽略。</b>
     * 请求里带了与原值不同的 {@code parentId} 时，说明调用方想移动 ——
     * 那要走 {@link #move}，因为它有自己的审计动作与更大的影响面。
     * 静默忽略的表现是"保存成功、层级没变"，调用方会以为改好了。
     * （传 {@code null} 或不传都表示"这次不改层级"，因此把节点提升为顶级也走 move 接口，
     * 那里传空 body 即可 —— 两种意图不会混淆。）
     *
     * <p><b>改名会重写整棵子树的 {@code full_name}</b>（下级全称里含着这一级的名字）。
     * 这是把全称做成派生字段的必然代价，也正是它必须由服务端维护的原因：
     * 交给调用方传，改名后就会留下"名字已改、全称还是旧的"这种半更新状态。
     */
    @Transactional
    public OrganizationVO update(Long id, OrganizationSaveRequest request) {
        OrganizationEntity entity = requireOrg(id);
        assertParentUnchanged(entity, request.getParentId());
        OrganizationEntity parent = parentOf(entity);

        String oldName = entity.getOrgName();
        String newName = request.getOrgName().trim();
        entity.setOrgName(newName);
        applyCommon(entity, request);
        updateColumns(entity);

        if (!newName.equals(oldName)) {
            rewriteSubtree(entity, parent);
        }
        return toVO(entity);
    }

    /**
     * 移动组织节点 —— **把整棵子树搬到另一个父级下**。
     *
     * <p>这是本类里影响面最大的一次写入：{@code DEPT_AND_CHILD} 档位按
     * {@code code_path} 前缀求子树，所以路径变了之后，
     * **子树上每个人的可见范围同时改变**。表面上只是"换了个上级部门"。
     * 因此它有自己的审计动作（{@code auth.org.move}），而不是混进一次普通更新 ——
     * 事后追问"这个部门的可见范围为什么变了"必须能一条过滤直接命中。
     *
     * <p>四件事按顺序做完才算成功：① 不能挂到自己或自己的后代下（否则成一棵转不出来的环）；
     * ② 重写自身与整棵子树的 {@code code_path} / {@code org_level} / {@code full_name}；
     * ③ 重算新父级的 {@code hasChild}；④ 重算**原**父级的 {@code hasChild}
     * （它可能因此变成叶子）。④ 最容易漏，漏了的症状是一个永远为 1 的展开箭头。
     */
    @Transactional
    public OrganizationVO move(Long id, OrganizationMoveRequest request) {
        OrganizationEntity entity = requireOrg(id);
        OrganizationEntity newParent = resolveParent(request.getParentId(), entity.getTenantId());
        assertNotSelfOrDescendant(entity, newParent);

        Long oldParentId = entity.getParentId();
        entity.setParentId(newParent == null ? null : newParent.getId());
        rewriteSubtree(entity, newParent);
        updateColumns(entity);

        refreshHasChild(entity.getParentId());
        if (oldParentId != null && !oldParentId.equals(entity.getParentId())) {
            refreshHasChild(oldParentId);
        }
        return toVO(entity);
    }

    /**
     * 删除组织（逻辑删除）。两道拒绝：有下级（{@code 40913}）、有成员归属（{@code 40919}）。
     *
     * <p>前者对应"删掉一棵子树会连带影响所有后代"，
     * 后者对应"挂在这个组织上的人的 DEPT 档位会静默退回仅本人"——
     * 删的本意可能只是"这个部门撤了"，实际效果是一批人少看一批数据，
     * 而没有任何界面会提示这件事发生过。两者都要求先把影响面缩小到能看懂的程度。
     *
     * <p><b>没有级联删除，也没有"连人一起删"的选项。</b>与 {@code UserGroupService.delete}
     * 同一条原则：不提供藏起来的级联。
     */
    @Transactional
    public void delete(Long id) {
        OrganizationEntity entity = requireOrg(id);

        Long childCount = organizationMapper.selectCount(Wrappers.<OrganizationEntity>lambdaQuery()
                .eq(OrganizationEntity::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw AuthResultCode.HIERARCHY_CYCLE.businessException(
                    "该组织下仍有 " + childCount + " 个下级组织，请先处理下级");
        }

        Long memberCount = relOrgUserMapper.selectCount(Wrappers.<RelOrgUserEntity>lambdaQuery()
                .eq(RelOrgUserEntity::getOrganizationId, id));
        if (memberCount != null && memberCount > 0) {
            throw AuthResultCode.ORG_IN_USE.businessException(
                    "该组织下仍有 " + memberCount + " 个成员归属，请先移除成员");
        }

        organizationMapper.deleteById(entity.getId());
        refreshHasChild(entity.getParentId());
    }

    /* ------------------------------------------------------------------ 取数与校验 */

    /** 供成员服务与数据范围求值复用 —— 它保证"拿到的一定是本租户内存在且未删除的组织" */
    OrganizationEntity requireOrg(Long id) {
        OrganizationEntity entity = id == null ? null : organizationMapper.selectById(id);
        if (entity == null) {
            throw AuthResultCode.ORGANIZATION_NOT_FOUND.businessException();
        }
        return entity;
    }

    /**
     * 同 {@link #requireOrg}，但**不存在时返回 null 而不是抛异常**。
     *
     * <p>只给数据范围求值用，而且这一处差别很关键：求值路径上"组织查不到"
     * （例如归属行还在、组织已被硬删）**不是一个业务失败**，它只是让这个人
     * 算不出精确范围、从而往窄兜底。在那里抛异常会把一次普通的查询变成 500 ——
     * 一个已经被删掉的部门会让所有引用到它的请求全部失败。
     */
    OrganizationEntity requireOrgOrNull(Long id) {
        return id == null ? null : organizationMapper.selectById(id);
    }

    /** 按主键批量取（成员列表要把组织名一次查全，不做 N+1） */
    List<OrganizationEntity> listByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return organizationMapper.selectList(Wrappers.<OrganizationEntity>lambdaQuery()
                .in(OrganizationEntity::getId, ids));
    }

    /**
     * 某组织**自身 + 全部启用的后代**的主键，供 {@code DEPT_AND_CHILD} 档位求值。
     *
     * <p>用 {@code code_path} 前缀匹配求子树 —— 这是那一列存在的**唯一理由**
     * （路径既是求值口径、也是"被重写的边界"，两者共用一个事实是有意的：
     * 若它们不一致，数据范围的边界就会与维护的边界脱节）。
     *
     * <p><b>已停用的后代被排除。</b>停用不级联（停用上级不影响下级各自的有效性），
     * 但被停用的节点自己不再是范围目标 —— 否则"停用这个部门"这个动作
     * 在它仍有下级时毫无效果，而那正是调用方最可能期待的相反结果。
     *
     * <p>调用方必须先确认 {@code root} 自身是启用的（{@code DataScopeService} 在
     * {@code primaryOrganizationOf} 里已经确认过）：根停用意味着这一档整体算不出，
     * 而不是"除了它自己都算得出"。
     */
    @Transactional(readOnly = true)
    public List<Long> enabledSubtreeIds(OrganizationEntity root) {
        String prefix = (root.getCodePath() == null ? "/" : root.getCodePath()) + root.getId() + "/";
        List<OrganizationEntity> descendants = organizationMapper.selectList(
                Wrappers.<OrganizationEntity>lambdaQuery()
                        .eq(OrganizationEntity::getTenantId, root.getTenantId())
                        .likeRight(OrganizationEntity::getCodePath, prefix)
                        .eq(OrganizationEntity::getStatus, Status.ENABLED.wireName())
                        .orderByAsc(OrganizationEntity::getOrgLevel)
                        .orderByAsc(OrganizationEntity::getId));

        List<Long> ids = new ArrayList<>(descendants.size() + 1);
        ids.add(root.getId());
        descendants.forEach(item -> ids.add(item.getId()));
        return ids;
    }

    /**
     * 租户：显式指定则**校验存在性**，否则取当前登录用户的租户。
     *
     * <p>组织只能被本租户的用户归属引用、也只能被本租户的
     * 数据范围求值命中，一个写错租户的组织会**永远算不出任何范围** ——
     * 树建得好好看、人也挂进去了、DEPT 档位却恒为空。校验住在
     * {@link TenantService#assertExists}（此前四个域各写一遍，见 ADR-0050）。
     */
    private Long resolveTenantId(Long requested) {
        if (requested != null) {
            tenantService.assertExists(requested);
            return requested;
        }
        Long current = AuthContextHolder.currentTenantId();
        if (current == null) {
            throw AuthResultCode.PARAM_INVALID.businessException("无法确定租户，请显式指定 tenantId");
        }
        return current;
    }

    /**
     * 父组织：必须存在、必须同租户，**且不能是已停用的**。
     *
     * <p>拒绝挂在停用组织下，与"不拒绝给已停用角色加成员"是**相反**的判断，值得说清为什么：
     * 后者是"先配置、后启用"的合理运维顺序（人先挂好、上线当天再启用角色），
     * 而组织树的层级一旦挂错地方，影响的是整棵子树的路径与所有人的可见范围 ——
     * 那不是一个开关就能纠正回来的状态。停用的部门下新增子部门，
     * 更可能是**挂错了位置**（本想挂在它的兄弟节点下）。
     */
    private OrganizationEntity resolveParent(Long parentId, Long tenantId) {
        if (parentId == null) {
            return null;
        }
        OrganizationEntity parent = organizationMapper.selectById(parentId);
        if (parent == null) {
            throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException(
                    "引用的父组织不存在：" + parentId);
        }
        if (!parent.getTenantId().equals(tenantId)) {
            throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException(
                    "父组织不属于同一租户，跨租户的层级会造成数据范围错位");
        }
        if (isDisabled(parent)) {
            throw AuthResultCode.PARAM_INVALID.businessException(
                    "父组织已被停用，不能在其下新增或移动到该节点："
                            + "停用的组织不再参与数据范围的路径求值，挂在它下面会得到一个算不出范围的分支");
        }
        return parent;
    }

    /** 取某个节点当前的父级（改名要重写子树的全称时需要父级的前缀） */
    private OrganizationEntity parentOf(OrganizationEntity entity) {
        return entity.getParentId() == null ? null : organizationMapper.selectById(entity.getParentId());
    }

    /**
     * 修改路径不接受层级变更 —— 带了不同的 {@code parentId} 就报错并指路。
     *
     * <p>只在**值非空且与原值不同**时拒绝：不传与传 {@code null} 都表示"这次不改层级"，
     * 这是唯一不会把"没传"与"想提升为顶级"混起来的处理方式
     * （提升为顶级请走 {@code PUT /api/auth/orgs/{id}/parent}，传空 body）。
     */
    private static void assertParentUnchanged(OrganizationEntity entity, Long requestedParentId) {
        if (requestedParentId == null) {
            return;
        }
        if (!requestedParentId.equals(entity.getParentId())) {
            throw AuthResultCode.PARAM_INVALID.businessException(
                    "修改接口不改变组织层级：请求里的 parentId(" + requestedParentId
                            + ") 与当前值(" + entity.getParentId() + ") 不同。"
                            + "移动节点请用 PUT /api/auth/orgs/{id}/parent —— "
                            + "它会重写整棵子树并留下独立的审计记录");
        }
    }

    /**
     * 拒绝把组织挂到自己或自己的后代下面。
     *
     * <p>与 {@code UserGroupService.assertNotSelfOrDescendant} 同一支写法（沿父链上溯），
     * 而不是拿 {@code code_path} 做前缀判断。两者在数据一致时等价，
     * 但上溯写法在两个方向上更稳：**数据被直接改坏时不会死循环**
     * （{@code seen} 集合终止），而路径判断会因为一份过期的路径而
     * **漏判**一个真实的环 —— 后者更糟，因为它不报错。
     */
    private void assertNotSelfOrDescendant(OrganizationEntity self, OrganizationEntity newParent) {
        if (newParent == null) {
            return;
        }
        if (newParent.getId().equals(self.getId())) {
            throw AuthResultCode.HIERARCHY_CYCLE.businessException("不能把组织挂到自己下面");
        }
        Set<Long> seen = new HashSet<>();
        OrganizationEntity cursor = newParent;
        while (cursor != null) {
            if (cursor.getId().equals(self.getId())) {
                throw AuthResultCode.HIERARCHY_CYCLE.businessException(
                        "不能把组织挂到自己的下级下面：" + newParent.getId());
            }
            if (!seen.add(cursor.getId())) {
                return;
            }
            cursor = cursor.getParentId() == null
                    ? null : organizationMapper.selectById(cursor.getParentId());
        }
    }

    /* ------------------------------------------------------------------ 派生列维护 */

    /**
     * 重写**自身与整棵子树**的三个派生列。建、改名、移动都走这一个方法。
     *
     * <p>为什么不拆成"移动时重写路径、改名时只重写全称"：两条路径的共同前提是
     * "派生列必须与 {@code parent_id}、各节点名称一致"，拆开只会让其中一条
     * 在未来被漏改。这个方法**是幂等的** —— 对没变过的子树调用一次，写回的
     * 值完全相同（除了 {@code update_time}），因此重复调用不会造成损坏。
     *
     * <p>子树用 {@code code_path} 前缀匹配取出（那是它唯一的用途之外的第二处用途：
     * 前缀匹配既是求值口径、也是这里的定位口径，两者共用一个事实是好事 ——
     * 若它们不一致，数据范围的边界与"被重写的边界"就会对不上）。
     *
     * <p>逐行 update 而不是一条 SQL 做前缀替换：三个方言的字符串函数
     * （{@code SUBSTRING} / {@code CONCAT} / {@code CHAR_LENGTH}）写法与语义都有差别，
     * 而组织树的规模是人手维护的量级。这笔交易见 {@code OrganizationMapper} 的注释。
     */
    private void rewriteSubtree(OrganizationEntity node, OrganizationEntity parent) {
        // 先把"旧"的三个值取出来：下面写到 node 上之后它们就没了
        String oldSelfPath = node.getCodePath() == null ? "/" : node.getCodePath();
        int oldSelfLevel = node.getOrgLevel() == null ? 1 : node.getOrgLevel();
        String oldPrefix = oldSelfPath + node.getId() + "/";

        String selfPath = pathOf(parent);
        int selfLevel = levelOf(parent);
        String selfFullName = namePathOf(parent, node.getOrgName());

        node.setCodePath(selfPath);
        node.setOrgLevel(selfLevel);
        node.setFullName(selfFullName);
        updateDerivedColumns(node);

        List<OrganizationEntity> descendants = organizationMapper.selectList(
                Wrappers.<OrganizationEntity>lambdaQuery()
                        .eq(OrganizationEntity::getTenantId, node.getTenantId())
                        .likeRight(OrganizationEntity::getCodePath, oldPrefix));
        if (descendants.isEmpty()) {
            return;
        }
        // 按层级升序：父级一定先于子级被处理，于是"新全称 = 父级的新全称 + / + 自己的名字"
        // 可以逐行推下去，不需要先建整棵树
        descendants.sort(Comparator
                .comparingInt((OrganizationEntity item) -> item.getOrgLevel() == null ? 0 : item.getOrgLevel())
                .thenComparing(OrganizationEntity::getId));

        int levelDelta = selfLevel - oldSelfLevel;
        Map<Long, String> newFullNames = new LinkedHashMap<>();
        newFullNames.put(node.getId(), selfFullName);

        String newPrefix = selfPath + node.getId() + "/";
        for (OrganizationEntity descendant : descendants) {
            String oldPath = descendant.getCodePath() == null ? "/" : descendant.getCodePath();
            // 前缀一定匹配（它就是按这个前缀查出来的）；substring 的兜底只为防御直接改库留下的脏数据
            String suffix = oldPath.startsWith(oldPrefix) ? oldPath.substring(oldPrefix.length()) : "";
            int oldLevel = descendant.getOrgLevel() == null ? oldSelfLevel + 1 : descendant.getOrgLevel();
            descendant.setCodePath(newPrefix + suffix);
            descendant.setOrgLevel(oldLevel + levelDelta);
            // 父级的新全称理应在表里（父级层级更低、已被处理）；查不到说明这段数据被直接改坏了，
            // 那种情况退回用本节点的新全称作前缀 —— 不修数据，但也不留一个 null
            String parentFullName = newFullNames.getOrDefault(descendant.getParentId(), selfFullName);
            String ownFullName = parentFullName + "/" + descendant.getOrgName();
            descendant.setFullName(ownFullName);
            newFullNames.put(descendant.getId(), ownFullName);
            updateDerivedColumns(descendant);
        }
    }

    /**
     * 重算某个节点的 {@code hasChild}（传 null 时什么都不做）。
     *
     * <p>删除、移动、复活之后都要调一次：它是**可以变小**的字段，
     * 而"只在从 0 变 1 时标记"（{@code TenantService} 的做法）会让最后一个人走了之后
     * 箭头还在。这个字段直接决定界面上画不画展开箭头，一个点开是空的箭头
     * 比没有箭头更让人怀疑数据。
     */
    private void refreshHasChild(Long orgId) {
        if (orgId == null) {
            return;
        }
        Long childCount = organizationMapper.selectCount(Wrappers.<OrganizationEntity>lambdaQuery()
                .eq(OrganizationEntity::getParentId, orgId));
        int expected = childCount != null && childCount > 0 ? 1 : 0;
        organizationMapper.update(new OrganizationEntity(),
                Wrappers.<OrganizationEntity>lambdaUpdate()
                        .eq(OrganizationEntity::getId, orgId)
                        .set(OrganizationEntity::getHasChild, expected));
    }

    /** 清空某个组织名下的全部用户归属 —— 只有"复活"一个调用点，让"复活 = 空部门"无条件成立 */
    private void clearMembersOf(Long orgId) {
        relOrgUserMapper.delete(Wrappers.<RelOrgUserEntity>lambdaQuery()
                .eq(RelOrgUserEntity::getOrganizationId, orgId));
    }

    /* ------------------------------------------------------------------ 组装 */

    private void applyCommon(OrganizationEntity entity, OrganizationSaveRequest request) {
        entity.setOrgName(request.getOrgName().trim());
        entity.setOrgType(FilterValue.text(request.getOrgType()));
        entity.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        entity.setStatus(resolveStatus(request.getStatus()));
        entity.setDescription(FilterValue.text(request.getDescription()));
        entity.setContactName(FilterValue.text(request.getContactName()));
        entity.setContactPhone(FilterValue.text(request.getContactPhone()));
        entity.setContactEmail(FilterValue.text(request.getContactEmail()));
        entity.setManagerId(request.getManagerId());
        // 负责人姓名在这里就解析出来，而不是留给 updateColumns —— 新建走的是 insert，
        // 不经过 updateColumns，那样新组织的 managerName 会永远是 null
        // （列表展示用的是姓名，于是"刚建的部门没有负责人"看起来像功能没做）
        entity.setManagerName(managerNameOf(request.getManagerId()));
    }

    /** 负责人姓名由服务端解析，入参不接受 —— 否则会出现"列表显示 A、详情指向 B"的两份不一致 */
    private String managerNameOf(Long managerId) {
        if (managerId == null) {
            return null;
        }
        UserEntity user = userMapper.selectById(managerId);
        return user == null ? null : displayNameOf(user);
    }

    /**
     * 更新可变列 —— **逐列显式 {@code set}，不用 {@code updateById}**。
     *
     * <p>MyBatis-Plus 默认的 {@code NOT_NULL} 字段策略只把**非空**字段拼进 {@code SET}，
     * 于是"把负责人清掉、把联系人清掉"发不出去：影响 0 行、不报错、返回 200。
     * 理由与写法与 {@code UserGroupService.updateColumns} 完全一致。
     *
     * <p><b>{@code parent_id} 必须在这里，不能只靠 {@link #rewriteSubtree}。</b>
     * 两个调用点都要落它，而它们都不是"改资料"：
     * <ul>
     *   <li>{@link #move} —— 换父级是这次写入的**全部内容**；</li>
     *   <li>{@link #create} 的复活分支 —— 一个曾被删掉的编码被重建时，
     *       它的父级可能已经从"某个部门下"变成了顶级（或反过来），
     *       而显式 {@code set} 正好能写 {@code null}（{@code updateById} 写不了）。</li>
     * </ul>
     * 漏掉的后果不是"少改了一列"，而是一个**半更新状态**：路径与层级已经指向新父级，
     * 父子关系却还在旧父级下 —— 树接口按 {@code parent_id} 拼，于是节点显示在原地；
     * 而 {@code DEPT_AND_CHILD} 按 {@code code_path} 求值，算出的是新位置。
     * 两者不一致时，界面上看到的层级与真实的数据范围会各说各话，且都不报错。
     * （这条不是推演出来的：本轮的端到端用例正是靠"原父级的 {@code hasChild}
     * 应当归 0"把它抓出来的。）
     *
     * <p>{@code update} 路径同样会写它，但那里先过了 {@link #assertParentUnchanged}，
     * 值与原值相等，因此是一次无副作用的写入。
     *
     * <p><b>三个派生列不在这里</b>（{@code org_level} / {@code code_path} / {@code full_name}）：
     * 它们由 {@link #updateDerivedColumns} 与 {@link #rewriteSubtree} 负责。
     * 拆开是因为它们的**触发条件不同** —— 改名要重写整棵子树的全称，
     * 而"改个联系人"不该顺手把孩子们的路径再算一遍。
     */
    private void updateColumns(OrganizationEntity entity) {
        organizationMapper.update(new OrganizationEntity(), Wrappers.<OrganizationEntity>lambdaUpdate()
                .eq(OrganizationEntity::getId, entity.getId())
                .set(OrganizationEntity::getParentId, entity.getParentId())
                .set(OrganizationEntity::getOrgName, entity.getOrgName())
                .set(OrganizationEntity::getOrgType, entity.getOrgType())
                .set(OrganizationEntity::getManagerId, entity.getManagerId())
                .set(OrganizationEntity::getManagerName, entity.getManagerName())
                .set(OrganizationEntity::getSortNo, entity.getSortNo())
                .set(OrganizationEntity::getStatus, entity.getStatus())
                .set(OrganizationEntity::getDescription, entity.getDescription())
                .set(OrganizationEntity::getContactName, entity.getContactName())
                .set(OrganizationEntity::getContactPhone, entity.getContactPhone())
                .set(OrganizationEntity::getContactEmail, entity.getContactEmail())
                .set(OrganizationEntity::getHasChild, entity.getHasChild()));
    }

    /** 只写派生列 —— 建、移动、重写子树时用，避免把业务字段一起再写一遍 */
    private void updateDerivedColumns(OrganizationEntity entity) {
        organizationMapper.update(new OrganizationEntity(), Wrappers.<OrganizationEntity>lambdaUpdate()
                .eq(OrganizationEntity::getId, entity.getId())
                .set(OrganizationEntity::getCodePath, entity.getCodePath())
                .set(OrganizationEntity::getOrgLevel, entity.getOrgLevel())
                .set(OrganizationEntity::getFullName, entity.getFullName()));
    }

    private static OrganizationVO toVO(OrganizationEntity entity) {
        OrganizationVO vo = new OrganizationVO();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setOrgCode(entity.getOrgCode());
        vo.setOrgName(entity.getOrgName());
        vo.setFullName(entity.getFullName());
        vo.setParentId(entity.getParentId());
        vo.setOrgLevel(entity.getOrgLevel());
        vo.setCodePath(entity.getCodePath());
        vo.setHasChild(entity.getHasChild());
        vo.setOrgType(entity.getOrgType());
        vo.setManagerId(entity.getManagerId());
        vo.setManagerName(entity.getManagerName());
        vo.setSortNo(entity.getSortNo());
        vo.setStatus(entity.getStatus());
        vo.setDescription(entity.getDescription());
        vo.setContactName(entity.getContactName());
        vo.setContactPhone(entity.getContactPhone());
        vo.setContactEmail(entity.getContactEmail());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private static OrganizationTreeNode toTreeNode(OrganizationEntity entity) {
        OrganizationTreeNode node = new OrganizationTreeNode();
        node.setId(entity.getId());
        node.setOrgCode(entity.getOrgCode());
        node.setOrgName(entity.getOrgName());
        node.setFullName(entity.getFullName());
        node.setParentId(entity.getParentId());
        node.setOrgLevel(entity.getOrgLevel());
        node.setCodePath(entity.getCodePath());
        node.setStatus(entity.getStatus());
        node.setManagerId(entity.getManagerId());
        node.setManagerName(entity.getManagerName());
        node.setSortNo(entity.getSortNo());
        return node;
    }

    private static String displayNameOf(UserEntity user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName();
    }

    /* ------------------------------------------------------------------ 派生规则 */

    /** 根为 1，子 = 父 + 1 */
    private static int levelOf(OrganizationEntity parent) {
        if (parent == null) {
            return 1;
        }
        return (parent.getOrgLevel() == null ? 1 : parent.getOrgLevel()) + 1;
    }

    /** 形如 {@code /1/5/}：各级**祖先**的主键加斜杠，不含自身；根为 {@code /} */
    private static String pathOf(OrganizationEntity parent) {
        if (parent == null) {
            return "/";
        }
        String parentPath = parent.getCodePath() == null ? "/" : parent.getCodePath();
        return parentPath + parent.getId() + "/";
    }

    /** 形如 {@code 蓝鲸集团/研发部/前端组}；父级全称为空时退化成只有自己 */
    private static String namePathOf(OrganizationEntity parent, String orgName) {
        if (parent == null || parent.getFullName() == null || parent.getFullName().isBlank()) {
            return orgName;
        }
        return parent.getFullName() + "/" + orgName;
    }

    private static boolean isDisabled(OrganizationEntity entity) {
        return Status.DISABLED.wireName().equalsIgnoreCase(entity.getStatus());
    }

    /**
     * 状态：缺省 {@code ENABLED}，**认不得就报错**（不像 {@code TenantService} 那样兜底成启用）。
     *
     * <p>这一列是真开关：停用的组织不再能作为 DEPT 档位的范围目标。把 {@code DISABLE}、
     * {@code OFF} 这类拼写悄悄存成"启用"，等于让一次"我要停用这个部门"的意图变成
     * 一次静默的"什么都没发生"——而调用方看到的是一句"保存成功"。
     */
    private static String resolveStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return Status.ENABLED.wireName();
        }
        Status parsed = Status.parse(raw);
        if (parsed == null) {
            throw AuthResultCode.PARAM_INVALID.businessException(
                    "状态取值不合法，只能是 ENABLED 或 DISABLED，收到：" + raw);
        }
        return parsed.wireName();
    }

}
