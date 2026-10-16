package com.bluewhale.sts.auth.tenant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.auth.common.enums.Status;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import com.bluewhale.sts.common.basic.model.PageResult;
import com.bluewhale.sts.common.db.query.FilterValue;
import com.bluewhale.sts.common.db.query.KeywordLike;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 租户应用服务。
 *
 * <p><b>唯一性校验放在服务层而不是只靠数据库唯一索引：</b>索引是最后一道防线，
 * 但让用户看到"Duplicate entry 'acme' for key 'uk_bw_auth_tenant_code'"这样的
 * 数据库原文，既没有可操作性也泄露了库结构。服务层先查一次给出明确文案，
 * 索引则负责挡住并发下的漏网之鱼 —— 两层各司其职，不是重复。
 */
@Service
public class TenantService {

    private final TenantMapper tenantMapper;

    public TenantService(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    @Transactional(readOnly = true)
    public PageResult<TenantVO> page(TenantQuery query) {
        // 关键词先规范化（吃掉两端空白）再转义，见 KeywordLike（decisions/0067 · 0068）。
        String pattern = KeywordLike.pattern(query.getKeyword());
        // 过滤值先规范化再进 eq（decisions/0069 · 0070）：值是无条件求值的，不能在这里调 .trim()。
        // status 是枚举型的列，走 enumValue —— 认得就换成标准写法（enabled → ENABLED），认不得按原样比。
        String status = FilterValue.enumValue(query.getStatus(), Status::parse, Status::wireName);
        var wrapper = Wrappers.<TenantEntity>lambdaQuery()
                .and(pattern != null, w -> w
                        .apply(KeywordLike.condition("tenant_code"), pattern)
                        .or().apply(KeywordLike.condition("tenant_name"), pattern))
                .eq(status != null, TenantEntity::getStatus, status)
                // 非平台级操作者只看得到**自己那一个**租户（decisions/0074）。
                // 恒定的条件，不是可关的写法 —— 与审计域那条"租户强制等于当前登录者"同源；
                // 没有身份上下文时 currentOperatorIsPlatform() 为真，这一条不生效
                // （内部调用本就不该被这条路挡）
                .eq(!currentOperatorIsPlatform(), TenantEntity::getId,
                        AuthContextHolder.currentTenantId())
                .orderByAsc(TenantEntity::getTenantLevel)
                .orderByAsc(TenantEntity::getId);

        Page<TenantEntity> page = tenantMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        List<TenantVO> records = page.getRecords().stream().map(TenantService::toVO).toList();
        return PageResult.of(records, page.getTotal(), query.getPage(), query.getSize());
    }

    @Transactional(readOnly = true)
    public TenantVO detail(Long id) {
        return toVO(requireVisibleTenant(id));
    }

    /**
     * 新建租户。
     *
     * <p><b>编码判重必须"看得见软删行"</b>，所以它走
     * {@link TenantMapper#selectAnyByCode} 而不是 {@code selectCount}：
     * 唯一索引 {@code uk_bw_auth_tenant_code} 不含 {@code deleted}，软删的租户仍然占着
     * 这个编码，而 {@code selectCount} 会被自动追加的 {@code deleted = 0} 挡住 ——
     * 判重通过、insert 撞键、由 {@code GlobalExceptionHandler} 翻成兜底的
     * {@code 40900}「数据已存在或与已有记录冲突」。故障不显形（不是 500），
     * 但文案退化成一句不含主语的话，而那句话原本的用途是"并发下的第二道防线"（ADR-0056）。
     *
     * <p><b>与另外四个域相反：这里看见软删行之后是"拒绝"，不是"复活"。</b>
     * 复活是否安全，取决于那一行的删除守卫有没有挡住活边。
     * 资源 / 角色 / 组织 / 用户组在删除时都拒绝了"仍被引用"（{@code 40913} /
     * {@code 40915} / {@code 40916} / {@code 40918} / {@code 40919}），
     * 所以它们的软删行一定是**没有活边的空壳**，复活只是把空壳放回来。
     * <b>租户不是这样</b>：{@link #delete} 只拒绝"仍有子租户"，
     * **不拒绝"还有人"** —— 一个已删租户名下可以有活着的用户，而
     * {@code AuthService#login} 先按租户编码找租户、找不到就按"凭证错误"处理，
     * 那些人的登录现在正因为租户被删而失败。复活会把这些人**一起放回来**：
     * 一次"用回同一个编码"的建租户操作静默地撤销了一次删除，而删除的人看不到这件事发生。
     * 用户域（{@code UserService#create}）出于同一理由做同样的选择。
     *
     * <p>两支（活行占着 / 软删行占着）给**同一个码**，刻意不再分一个出来：
     * 调用方要做的事完全一样（换一个编码），与 {@code 40920} / {@code 40002} 那种
     * "处置完全不同才分码"的情形不是一回事。也刻意**不**为软删那一支另写一句文案 ——
     * 它既不可操作，又把"软删"这个内部状态透给了调用方。
     *
     * <p><b>这里刻意没有名称判重。</b>租户名是**标签**，不是标识 —— 指认一个租户的是
     * {@code tenantCode}：登录按它找租户、前端拿它当行标识（{@code tenant-row-<code>}）、
     * 弹窗标题也用它，而名称旁边永远跟着编码。四个层级域（租户 / 组织 / 角色 / 用户组）
     * **都带 {@code parent_id}**，层级的语义就是"同一个名字可以在不同上级下复用"；
     * {@code 0030} 早已为组织写下这条理由，{@code 0061} 把它升成四域共用。
     *
     * <p><b>{@code TENANT_NAME_EXISTS} 曾落在这里，是四域里唯一的例外</b>（{@code 0062}）：
     * 它既没有语义依据，又没有唯一索引兜底 —— {@code tenant_name} 上没有索引，
     * 并发下的两个同名请求都能穿过"先查后写"，于是一道**以为在保证唯一、实际保证不了**
     * 的校验，同时拦掉了"两个碰巧同名的公司"这种合法情形。
     * <b>判一个列该不该挡重名，看的是它在语义上是否指认唯一对象，不是它有没有索引</b>：
     * 编码 / 用户名指认系统内的对象（加索引 ⇒ 数据约束级唯一）、
     * 邮箱 / 手机号指认现实世界的主体（服务层挡，有意接受并发漏网）、
     * 名称谁也不指认（不挡）。
     */
    @Transactional
    public TenantVO create(TenantSaveRequest request) {
        // 层级权限**排在编码判重之前**：先判重的话，一个非平台级操作者能从
        // "40904 编码已存在" vs "40302" 的差别里读出"这个编码被谁占着"——
        // 而那个租户对他而言并不存在（见 decisions/0074）
        assertCanCreateUnder(request.getParentId());
        if (tenantMapper.selectAnyByCode(request.getTenantCode()) != null) {
            throw AuthResultCode.TENANT_CODE_EXISTS.businessException();
        }

        TenantEntity parent = resolveParent(request.getParentId());

        TenantEntity entity = new TenantEntity();
        entity.setTenantCode(request.getTenantCode());
        entity.setTenantName(request.getTenantName());
        entity.setParentId(parent == null ? null : parent.getId());
        entity.setTenantLevel(parent == null ? 1 : (parent.getTenantLevel() == null ? 1 : parent.getTenantLevel()) + 1);
        entity.setCodePath(parent == null ? "/" : parent.getCodePath() + parent.getId() + "/");
        entity.setHasChild(0);
        entity.setStatus(resolveStatus(request.getStatus()));
        entity.setDescription(request.getDescription());
        entity.setAllowRegister(orZero(request.getAllowRegister()));
        entity.setAllowAdd(orZero(request.getAllowAdd()));
        tenantMapper.insert(entity);

        refreshHasChild(entity.getParentId());
        return toVO(entity);
    }

    @Transactional
    public TenantVO update(Long id, TenantSaveRequest request) {
        TenantEntity entity = requireVisibleTenant(id);

        // 名称与 create 一样不判重（理由见 create 的 javadoc）：把一个租户改成与别人同名，
        // 不会让任何人做错事 —— 指认租户的是 tenantCode，而它根本不可改。
        // 若只删 create 那一处、留着这里，就会出现"建得出来、改不出来"的怪状态。

        // tenantCode 不在修改项内：它是对外稳定标识，改了等于让所有引用它的地方失效。
        // 若确需变更，应当是"新建 + 迁移"的独立操作，而不是一次普通的字段更新。
        entity.setTenantName(request.getTenantName());
        entity.setDescription(request.getDescription());
        // 写入侧先归一，判据再拿**归一后**的值去比（decisions/0071）。此前这两件事都是
        // 拿请求里的字面量做的，于是同一个操作有两副面孔：
        //   ① 传小写的 "enabled" 被当成"写入的值不是 ENABLED" ⇒ 判定为"我要停用自己所在的
        //      租户" ⇒ 40921，而调用方唯一做错的事只是拼写用了小写；
        //   ② 改的是**别的**租户时，那个 "enabled" 原样落库 ⇒ 所有读者都在比
        //      `status == "ENABLED"`（见 AuthResultCode.TENANT_DISABLED 的说明），
        //      于是**那个租户整个登不进来** —— 一次保存成功把一整个租户锁在门外。
        // 归一到标准写法之后两条自动消失：判据与读者看的是同一个值，且它就是 Status 的真值。
        // 外层判空仍然要在 —— 不传 status 表示"不改这一列"，不是"改成 ENABLED"。
        String requestedStatus = FilterValue.text(request.getStatus());
        if (requestedStatus != null) {
            String status = resolveStatus(requestedStatus);
            // 判据写的是"**写入的值不是 ENABLED**"，而不是"从 ENABLED 变成别的"：
            // 两种写法在正常输入上等价，前者顺带把"写进一个非法值"那一支也挡了
            if (!Status.ENABLED.wireName().equals(status)) {
                assertNotOwnTenant(id);
            }
            entity.setStatus(status);
        }
        if (request.getAllowRegister() != null) {
            entity.setAllowRegister(request.getAllowRegister());
        }
        if (request.getAllowAdd() != null) {
            entity.setAllowAdd(request.getAllowAdd());
        }
        tenantMapper.updateById(entity);
        return toVO(entity);
    }

    @Transactional
    public void delete(Long id) {
        TenantEntity entity = requireVisibleTenant(id);
        // 与"停用自己所在租户"同一个码、同一条理由：删除是逻辑删除（@TableLogic），
        // 被删的租户在应用层等于不存在，对登录的影响与停用一模一样
        assertNotOwnTenant(id);
        Long childCount = tenantMapper.selectCount(Wrappers.<TenantEntity>lambdaQuery()
                .eq(TenantEntity::getParentId, id));
        if (childCount != null && childCount > 0) {
            // 级联删除会把整棵子树连同其数据一并抹掉，这类操作必须显式确认，
            // 不能藏在一个 delete 接口里顺手完成
            throw AuthResultCode.HIERARCHY_CYCLE.businessException("该租户下仍有子租户，请先处理子租户");
        }
        tenantMapper.deleteById(entity.getId());
        refreshHasChild(entity.getParentId());
    }

    /**
     * 校验一个**被引用的**租户确实存在。参数非空由调用方保证。
     *
     * <p><b>与 {@link #requireTenant} 是两件事，错误码也不同，刻意不合并：</b>
     * {@code requireTenant} 回答的是"你要操作的那个租户在哪"（找不到 → {@code 40402}，
     * 路径主语就是这个租户）；本方法回答的是"你引用的那个外键指向谁"
     * （指不到 → {@code 40002}，主语是**另一个**资源，租户只是它的一个字段）。
     * 合并成一个码会让"我请求的租户不存在"与"我传的 tenantId 指向不存在的东西"
     * 对调用方长得一模一样。
     *
     * <p><b>它存在的理由是"这条规则此前有四个属主"：</b>{@code bw_auth_user} /
     * {@code bw_auth_role} / {@code bw_auth_organization} / {@code bw_auth_user_group}
     * 各自都带 {@code tenant_id}，而**没有任何外键约束**（DDL 里零 {@code FOREIGN KEY}，
     * 见 {@code V3__create_bw_auth_tables.sql}）—— 建不出来"属于不存在租户的行"
     * 这件事只能由应用层负责。此前四个域各写一遍同名私有方法，其中用户域那一份
     * **漏掉了校验**，而另外三份的注释把它写成"有意不同"却无人负责（见 ADR-0050）。
     * 现在这条规则只有一个属主：调用方问这里，而不是各自伸手指向租户表。
     */
    @Transactional(readOnly = true)
    public void assertExists(Long tenantId) {
        requireReferencedTenant(tenantId);
    }

    /**
     * 校验一个**被引用的**租户现在能承载新数据（存在 + 启用）。参数非空由调用方保证。
     *
     * <p>与 {@link #assertExists} 是同一个判据的两格，**刻意分成两个方法而不是一个**：
     * 前者回答"这个外键指得到东西吗"，后者回答"指到的东西现在能用吗"。
     * 只有**新建**路径需要后一格 —— 查询路径继续只问 {@code assertExists}，
     * 因为"这个被停用的租户里现在有什么"正是排查它时最需要的信息（见 ADR-0051）。
     *
     * <p><b>为什么"存在"不够，以及它为什么不去问
     * {@link #resolveQueryTenant} / {@link #resolveWriteTenant}：</b>
     * 后两者回答的是"这次请求该落在哪个租户上"（隔离判定就在那里，见 {@code decisions/0074}），
     * 而这一格回答的是"那个租户现在能不能承载新数据" —— 把状态判据塞进解析里，
     * 等于连**读**一起关掉。所以这一格由调用方**显式**问，位置上只在 {@code create} 里
     * 出现一次，与解析里那一格形成对照，两处的差别就是"读侧不挡"这条边界。
     * （{@code resolveWriteTenant} 内部先过 {@link #assertExists} 再判隔离，顺序不可反：
     * 不存在的租户应当给出"引用不存在"，而不是被当成一次越权。）
     *
     * <p>复用 {@code requireReferencedTenant}（同一次查询、同一份文案），
     * 所以两个方法在"租户不存在"这一点上给出的码与话一模一样 —— 分成两个方法
     * 不该让同一种输入出现两种答案。
     */
    @Transactional(readOnly = true)
    public void assertAcceptsNewData(Long tenantId) {
        TenantEntity tenant = requireReferencedTenant(tenantId);
        if (!Status.ENABLED.wireName().equals(tenant.getStatus())) {
            throw AuthResultCode.TENANT_DISABLED.businessException(
                    "引用的租户已停用，无法在其下新建数据：" + tenantId);
        }
    }

    /**
     * {@code assertExists} / {@code assertAcceptsNewData} 共用的那一半：查得到、且查得出。
     *
     * <p>逻辑删除不在这里判 —— {@code TenantEntity} 继承的 {@code BaseEntity}
     * 在 {@code deleted} 上标了 {@code @TableLogic}，{@code selectById} 生成的 SQL
     * 自带 {@code deleted = 0}，所以"已删租户"在应用层**就等于不存在**，
     * 走的就是这一条的 {@code 40002}，不需要第二条判据（有 {@code TenantReferenceApiTest}
     * 钉住这件事 —— 它此前只是推测，而且那个注解在离本域很远的 {@code BaseEntity} 里）。
     */
    private TenantEntity requireReferencedTenant(Long tenantId) {
        TenantEntity entity = tenantMapper.selectById(tenantId);
        if (entity == null) {
            throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException(
                    "引用的租户不存在：" + tenantId);
        }
        return entity;
    }

    /* ------------------------------------------------------------------ 租户边界（decisions/0074） */

    /**
     * 当前操作者是否站在**平台级**的位置。判据是结构性的：**它所在的租户是顶级租户**
     * （{@code parent_id IS NULL}；本仓的形态就是种子租户那一个，
     * {@code DefaultAdminSeeder} 写的 {@code tenantLevel = 1} / {@code codePath = "/"}）。
     *
     * <p><b>为什么用层级位置而不是新加一个权限码：</b>层级位置是**可以从库里读出来的事实**，
     * 而一个"平台管理员"码会造出两个都必须对上的开关（码 + 位置），
     * 且它们不一致时的表现是"某个顶级的账号什么都不能管"这种没人会去查的静默状态。
     * {@code 0058} 早已把"另一个租户里持有 {@code auth:tenant:update} 的账号
     * （本仓的形态就是种子租户的管理员）"当作既有的跨租户恢复入口 —— 本片只是把它命名了。
     *
     * <p><b>所在租户查不到时不视为平台级。</b>判据在数据异常时必须能往**窄**走：
     * 一个指向不存在租户的会话（租户被删了）不该因此获得全库权限。
     *
     * <p><b>没有身份上下文时视为平台级。</b>与 {@code assertNotOwnTenant}、{@code 0050} 的
     * "没有身份就没有那个操作者"同一条先例：无上下文的调用只出现在**内部路径**
     * （种子引导 {@code DefaultAdminSeeder}、测试夹具），它们本来就要求显式传入租户，
     * 而 HTTP 上的每一个写路径都在 {@code @RequiresPermission} 之后 —— 一定有上下文。
     * 若这里返回 false，种子会在启动时被自己的隔离挡住（它建组织时没有请求上下文）。
     */
    @Transactional(readOnly = true)
    public boolean currentOperatorIsPlatform() {
        Long own = AuthContextHolder.currentTenantId();
        if (own == null) {
            return true;
        }
        TenantEntity ownTenant = tenantMapper.selectById(own);
        return ownTenant != null && ownTenant.getParentId() == null;
    }

    /**
     * **查询路径**的租户解析：非平台级操作者传进来的 {@code tenantId} 被**忽略**，
     * 一律落在自己租户上。
     *
     * <p>沿用 {@code 0025} 第 1 / 3 条对审计查询立下的两条口径：租户条件是**恒定加上**的
     * （不是 {@code eq(条件, ...)} 那种可关的写法），而一次越权尝试只该**安静地**返回
     * 调用方自己有权看的那一份 —— "被忽略"与"筛出一个空结果"在调用方看来必须同形，
     * 报 {@code 403} 会把一次探测变成可观测的信号。
     *
     * <p>与 {@link #resolveWriteTenant} **刻意分成两个方法**：同一个 {@code tenantId} 参数
     * 在 query 里是**过滤条件**（越权 → 忽略），在 create 里是**归属声明**（越权 → 拒绝）。
     * 静默把"我要求在乙租户建号"改成"在甲租户建了一个号"比拒绝更糟 ——
     * 调用方以为建成的地方与实际不符，而他看不到任何异常。
     */
    @Transactional(readOnly = true)
    public Long resolveQueryTenant(Long requested) {
        if (requested == null) {
            return requireCurrentTenant();
        }
        if (currentOperatorIsPlatform()) {
            assertExists(requested);
            return requested;
        }
        Long own = requireCurrentTenant();
        return requested.equals(own) ? requested : own;

    }

    /**
     * **写入路径**的租户解析：非平台级操作者带别家的 {@code tenantId} 一律**显式拒绝**。
     *
     * <p>拒绝时用的码与文案与"引用的租户不存在"**一模一样**（{@code 40002} +
     * {@code "引用的租户不存在：" + id}）：对非平台级操作者而言，别家租户在系统里
     * **就是不存在**（他连租户列表里都看不到它，见 {@code TenantService#page}）。
     * 同码同文案是这条"不可区分"的完整表述 —— 只同码而文案不同，等于换了个地方泄漏。
     */
    @Transactional(readOnly = true)
    public Long resolveWriteTenant(Long requested) {
        if (requested == null) {
            return requireCurrentTenant();
        }
        if (currentOperatorIsPlatform()) {
            assertExists(requested);
            return requested;
        }
        Long own = requireCurrentTenant();
        if (!requested.equals(own)) {
            throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException(
                    "引用的租户不存在：" + requested);
        }
        return requested;
    }

    /**
     * 一个**已存在对象**的租户归属是否对当前操作者可见，不可见则按"不存在"抛出。
     *
     * <p><b>它不覆盖文案，这一点是 {@code 0075} 订正过的。</b>{@code 0074} 立这条时用的是
     * 覆盖文案（{@code notFoundCode.getMessage() + "或不属于当前租户"}），理由是
     * "同一个码在所有这些落点上给出同一句话"。**那个理由推错了方向**：覆盖之后，
     * "东西真的不存在"那一支（{@code requireXxx} 里的 {@code entity == null}）说的仍是
     * 裸文案，于是同一个码在两条分支上给出**两句不同的话** —— 调用方比一下 {@code message}
     * 就学到了"这条记录确实存在，只是在别家"，而"它存在"本身就是别人的信息
     * （{@code 0025} 第 2 条、{@code 0059} 第二节）。现在两条分支都用裸文案：
     * **对看不见它的操作者而言它就是不存在，所以只有一句话可说。**
     *
     * <p>无身份上下文时直接通过：那是内部路径（见 {@link #currentOperatorIsPlatform()}）。
     */
    @Transactional(readOnly = true)
    public void assertVisible(Long entityTenantId, AuthResultCode notFoundCode) {
        if (entityTenantId == null) {
            return;
        }
        Long own = AuthContextHolder.currentTenantId();
        if (own == null || entityTenantId.equals(own) || currentOperatorIsPlatform()) {
            return;
        }
        throw notFoundCode.businessException();
    }

    /**
     * **写入侧**的一致性检查：这次写入引用的那个对象，与容器在同一个租户吗。
     *
     * <p><b>它与 {@link #assertVisible} 是两格，都会在"跨租户"上失败，但含义不同：</b>
     * 那一格问"这个已存在的对象对当前操作者可见吗"（不可见 → 该对象的 {@code 404}）；
     * 这一格问"我看得见的这个对象，和我要把它挂上去的那个容器对得上吗"（对不上 → {@code 40002}）。
     * 前者是"你看不到它"，后者是"你看到了，但它与容器不在同一租户，判定永远匹配不上"。
     *
     * <p><b>文案随读者分化，判据是"这位读者能不能对两种成因采取不同的行动"：</b>
     * <ul>
     *   <li><b>平台级操作者</b>是唯一能合法跨租户的人（跨租户预配就是他的工作）。
     *       他写错了租户与写错了主键**要做的事完全不同**（改租户归属 / 改 id），
     *       所以拿到 {@code diagnostic} 那句原文；</li>
     *   <li><b>非平台级操作者</b>看到的与"引用的 X 不存在"**逐字相同**。他不可能合法地
     *       持有别家对象的 id（{@code 0074} 决策 4 的原话：别家租户在系统里**就是不存在**，
     *       他连租户列表里都看不到它）⇒ 那一支对他只有一种成因。两种成因给两句话，
     *       等于告诉他"那个 id 确实存在，只是在别家" —— 逐个试一遍就能数出别家租户里
     *       有几个用户。**"是不是两件事"看的是读者能不能分开处置，不是数据上是不是同一件事。**</li>
     * </ul>
     *
     * <p>两个租户 id 有一个为 {@code null} 时算不一致：{@code tenant_id} 是三方言
     * {@code NOT NULL}（{@code bw_auth_resource} 那一个除外，它不走这条路），
     * 真出现 null 只可能是数据被直接改坏了 —— 那时不该静默通过。
     *
     * @param referrerTenantId   引用者（容器）所在租户
     * @param referencedTenantId 被引用对象所在租户
     * @param referencedLabel    被引用对象的称呼，必须与 {@code diagnostic} 里点名的那个对象一致
     * @param referencedId       被引用对象的主键，会被写进文案
     * @param diagnostic         平台级读者看到的那句诊断
     */
    @Transactional(readOnly = true)
    public void assertReferencedInSameTenant(Long referrerTenantId, Long referencedTenantId,
                                             String referencedLabel, Long referencedId,
                                             String diagnostic) {
        if (referrerTenantId != null && referencedTenantId != null
                && referrerTenantId.equals(referencedTenantId)) {
            return;
        }
        throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException(
                currentOperatorIsPlatform()
                        ? diagnostic
                        : "引用的" + referencedLabel + "不存在：" + referencedId);
    }

    /** 当前登录者所在的租户；没有身份要求显式传 {@code tenantId} —— 与四个业务域此前的分支同源 */
    private Long requireCurrentTenant() {
        Long current = AuthContextHolder.currentTenantId();
        if (current == null) {
            throw AuthResultCode.PARAM_INVALID.businessException("无法确定租户，请显式指定 tenantId");
        }
        return current;
    }

    private TenantEntity requireTenant(Long id) {
        TenantEntity entity = id == null ? null : tenantMapper.selectById(id);
        if (entity == null) {
            throw AuthResultCode.TENANT_NOT_FOUND.businessException();
        }
        return entity;
    }

    /**
     * 详情 / 修改 / 删除共用的取用：先"存在"（{@code 40402}），再"可见"（同一个码、
     * 且**同一句话** —— 见 {@link #assertVisible}）。
     *
     * <p>租户的"租户归属"就是它自己 —— 因此这里把 {@code entity.getId()} 交给
     * {@link #assertVisible}，与用户域传 {@code entity.getTenantId()} 是同一个判据的两种写法。
     */
    private TenantEntity requireVisibleTenant(Long id) {
        TenantEntity entity = requireTenant(id);
        assertVisible(entity.getId(), AuthResultCode.TENANT_NOT_FOUND);
        return entity;
    }

    /**
     * 建租户时的层级权限：非平台级操作者**只能在自己的租户下建子租户**。
     *
     * <p>两个分支给两个码，因为它们说的不是一件事：
     * <ul>
     *   <li>不传 {@code parentId}（建顶级）→ {@code 40302}。顶级租户不是"某个看不见的对象"，
     *       它是一个**能力**；这里也没有"那个东西存在"可言（它正要被创建）；</li>
     *   <li>{@code parentId} 指向别家租户 → {@code 40402} + **与"租户不存在"逐字相同的
     *       一句话**（见 {@link #resolveWriteTenant} 的理由：对非平台级操作者而言，
     *       别家租户在系统里就是不存在）。**这里不再覆盖文案**（{@code 0075}）：
     *       覆盖之后同一件事在不同入口有两句说法，而 {@code requireTenant} 那一句才是
     *       "租户不在"的通用答案。</li>
     * </ul>
     *
     * <p>第一条同时是"平台级"这个身份定义的**闭合条件**：没有它，
     * "建一个顶级租户"就等于给自己升一级，而那个新账号会立刻获得全库的可见性。
     */
    private void assertCanCreateUnder(Long parentId) {
        if (currentOperatorIsPlatform()) {
            return;
        }
        if (parentId == null) {
            throw AuthResultCode.PLATFORM_SCOPE_REQUIRED.businessException();
        }
        if (!parentId.equals(AuthContextHolder.currentTenantId())) {
            throw AuthResultCode.TENANT_NOT_FOUND.businessException();
        }
    }

    /**
     * 拒绝把**自己所在的租户**停用或删除（{@code 40921}）。
     *
     * <p><b>它为什么和"续期也判租户状态"必须同一次落地：</b>在那条判据落地**之前**，
     * "停用自己所在租户"其实是有退路的 —— 访问令牌还能用，而它把 {@code status}
     * 改回来就行（{@code 0051} 当初实测过这一点，并因此把这件事记成"没那么急"）。
     * 续期那条路一封，这个动作就变成了"所有会话在访问令牌到期后失效、而重新登录被拒"，
     * 恢复只剩"另一个租户里持有 {@code auth:tenant:update} 的账号"，根租户自己则只剩改库。
     * 两问是耦合的，所以 {@code docs/decisions/0058} 把它们一起答了。
     *
     * <p><b>它不拦别的租户。</b>判据是**身份与被操作对象的相等关系**，不是操作本身 ——
     * 管理别的租户正是这两个接口存在的理由（反例见 {@code TenantDisableApiTest}
     * 的前两例：停用别的租户照常成功）。
     *
     * <p><b>没有身份上下文时不判</b>（{@code currentTenantId()} 为 {@code null}）。
     * 这个守卫要挡的是"操作者把自己关在门外"—— 没有身份就没有那个操作者。
     * 所有写路径都在 {@code @RequiresPermission} 之后，因此 HTTP 上不存在这一支。
     */
    private void assertNotOwnTenant(Long id) {
        Long own = AuthContextHolder.currentTenantId();
        if (own != null && own.equals(id)) {
            throw AuthResultCode.SELF_TENANT_PROTECTED.businessException();
        }
    }

    private TenantEntity resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return requireTenant(parentId);
    }

    /**
     * 重算某个节点的 {@code hasChild}（传 null 时什么都不做）。
     *
     * <p><b>与 {@code OrganizationService#refreshHasChild} 是同一个语义，两侧同形是刻意的。</b>
     * 此前这里是 {@code markParentHasChild}：只在父级从 0 变 1 时写一次，**反向不重算**。
     * 那个写法给的理由是"租户树没有移动、层级极少变"，但**删除**在租户侧是存在的，
     * 而删除正是唯一能把它从 1 变 0 的动作 —— 理由不覆盖它要解释的事实。
     * 症状：建一个子租户、再把它删掉之后，父级的 {@code hasChild} 会**永远留在 1**，
     * 于是详情与列表对一个不存在的子节点一直说"是"（ADR-0053）。
     *
     * <p><b>它不参与任何判定</b> —— 两处 {@code delete} 的守卫都是真查一次
     * {@code parent_id} 的计数，从不读这个列。所以这里修的是"出参少说一句假话"，
     * 不是一条安全边界；也正因如此，改动没有回归风险。
     *
     * <p>代价是每次建/删多一次 {@code selectCount}（原先"已是 1 就直接返回"时省掉了那次
     * 写入）。这个不对称在两张表上都一样：换来的是一条**不需要记住**的规矩 ——
     * 派生列一律与真值同源，而不是"记得在哪个方向上也更新一下"。
     */
    private void refreshHasChild(Long parentId) {
        if (parentId == null) {
            return;
        }
        Long childCount = tenantMapper.selectCount(Wrappers.<TenantEntity>lambdaQuery()
                .eq(TenantEntity::getParentId, parentId));
        int expected = childCount != null && childCount > 0 ? 1 : 0;
        tenantMapper.update(new TenantEntity(), Wrappers.<TenantEntity>lambdaUpdate()
                .eq(TenantEntity::getId, parentId)
                .set(TenantEntity::getHasChild, expected));
    }

    private static String resolveStatus(String status) {
        Status parsed = Status.parse(status);
        return parsed == null ? Status.ENABLED.wireName() : parsed.wireName();
    }

    private static Integer orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static TenantVO toVO(TenantEntity entity) {
        TenantVO vo = new TenantVO();
        vo.setId(entity.getId());
        vo.setTenantCode(entity.getTenantCode());
        vo.setTenantName(entity.getTenantName());
        vo.setParentId(entity.getParentId());
        vo.setTenantLevel(entity.getTenantLevel());
        vo.setStatus(entity.getStatus());
        vo.setDescription(entity.getDescription());
        vo.setAllowRegister(entity.getAllowRegister());
        vo.setAllowAdd(entity.getAllowAdd());
        vo.setHasChild(entity.getHasChild());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

}
