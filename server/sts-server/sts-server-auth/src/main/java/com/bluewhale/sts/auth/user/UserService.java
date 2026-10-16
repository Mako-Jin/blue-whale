package com.bluewhale.sts.auth.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.auth.authentication.PasswordChanger;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.auth.common.enums.PasswordChangeSource;
import com.bluewhale.sts.auth.common.enums.Status;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import com.bluewhale.sts.auth.role.RoleMemberService;
import com.bluewhale.sts.auth.tenant.TenantService;
import com.bluewhale.sts.common.basic.model.PageResult;
import com.bluewhale.sts.common.db.query.FilterValue;
import com.bluewhale.sts.common.db.query.KeywordLike;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户应用服务。
 *
 * <p><b>租户隔离的默认行为：</b>查询与新增未显式指定 {@code tenantId} 时，
 * 一律落到当前登录用户所属租户。这是**安全默认值** ——
 * 如果默认是"不限租户"，任何一个忘记传参的调用点都会变成一次跨租户数据泄露，
 * 而这种漏洞在评审时几乎看不出来（参数没传，代码看着很正常）。
 *
 * <p><b>而"显式指定"那一侧同样要过一道关：</b>指定的租户必须存在
 * （{@link TenantService#assertExists}）。这一道此前**只有本域没有**，
 * 另外三个带 {@code tenant_id} 的域（角色 / 组织 / 用户组）都有 ——
 * 于是这里能建出属于不存在租户的账号，而租户表上没有任何外键能兜住它（见 ADR-0050）。
 *
 * <p><b>口令不在这里设。</b>强度校验、防重用、历史留痕三件事的实现在
 * {@link PasswordChanger} —— 本类（管理员重置）与 {@code AuthService}（自助改密）
 * 都只是它的调用方。**"换口令后撤销该账号的全部会话"同样在它那里**：
 * 这件事两个调用方取值完全一致，因此不属于任何调用方，见 {@code docs/decisions/0046}。
 * 错误码仍是 {@code 40003}（这次带的口令不合规）与
 * {@code 40914}（口令合法但撞了），分开是为了让前端能给出不同的引导，
 * 详见 {@link AuthResultCode}。
 *
 * <p><b>建号还会带出一个动作：按租户的「默认角色」自动授予。</b>规则本身在
 * {@link RoleMemberService#grantDefaultRolesTo}（那是"把角色授给谁"的属地），
 * 本类只负责在正确的时刻调用它、并如实把结果带回去。注入方向是
 * {@code UserService → RoleMemberService}，反向没有依赖 —— 不是巧合：
 * 成员关系的校验要查用户表（Mapper，不是本类），而用户不关心角色怎么授。
 */
@Slf4j
@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordChanger passwordChanger;
    private final RoleMemberService roleMemberService;
    private final TenantService tenantService;

    public UserService(UserMapper userMapper,
                       PasswordChanger passwordChanger,
                       RoleMemberService roleMemberService,
                       TenantService tenantService) {
        this.userMapper = userMapper;
        this.passwordChanger = passwordChanger;
        this.roleMemberService = roleMemberService;
        this.tenantService = tenantService;
    }

    @Transactional(readOnly = true)
    public PageResult<UserVO> page(UserQuery query) {
        Long tenantId = tenantService.resolveQueryTenant(query.getTenantId());
        // 关键词先规范化（吃掉两端空白）再转义，见 KeywordLike（decisions/0067 · 0068）。
        String pattern = KeywordLike.pattern(query.getKeyword());
        // 过滤值先规范化再进 eq（decisions/0069 · 0070）：`eq(条件, 列, 值)` 的"值"是
        // **无条件求值**的，写成 `query.getStatus().trim()` 会在 status 为空时当场 NPE。
        // 这条坑在本仓已踩过多次。status 是枚举型的列，因此走 enumValue —— 它比 text 多一步
        // "认得就换成标准写法"，`?status=enabled` 与 `status=ENABLED` 于是同义。
        String status = FilterValue.enumValue(query.getStatus(), Status::parse, Status::wireName);
        var wrapper = Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getTenantId, tenantId)
                .and(pattern != null, w -> w
                        .apply(KeywordLike.condition("username"), pattern)
                        .or().apply(KeywordLike.condition("display_name"), pattern)
                        .or().apply(KeywordLike.condition("email"), pattern))
                .eq(status != null, UserEntity::getStatus, status)
                .orderByDesc(UserEntity::getId);

        Page<UserEntity> page = userMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        List<UserVO> records = page.getRecords().stream().map(UserService::toVO).toList();
        return PageResult.of(records, page.getTotal(), query.getPage(), query.getSize());
    }

    @Transactional(readOnly = true)
    public UserVO detail(Long id) {
        return toVO(requireUser(id));
    }

    /**
     * 新建用户。
     *
     * <p><b>登录名判重必须"看得见软删行"</b>，所以它走
     * {@link UserMapper#selectAnyByTenantAndUsername} 而不是 {@code selectCount}：
     * 唯一索引 {@code uk_bw_auth_user_tenant_username} 不含 {@code deleted}，
     * 软删的账号仍然占着这个登录名，而 {@code selectCount} 会被自动追加的
     * {@code deleted = 0} 挡住 —— 判重通过、insert 撞键、由 {@code GlobalExceptionHandler}
     * 翻成兜底的 {@code 40900}「数据已存在或与已有记录冲突」。故障不显形（不是 500），
     * 但文案退化成一句不含主语的话，而那句话原本的用途是"并发下的第二道防线"（ADR-0056）。
     *
     * <p><b>与资源 / 角色 / 组织 / 用户组四个域相反：看见软删行之后是"拒绝"，不是"复活"。</b>
     * 那四个域在删除时都拒绝了"仍被引用"，所以它们的软删行是个没有活边的空壳。
     * 账号不是这样：{@link #delete} 只拒绝"删自己"，一个已删账号身上可能还挂着
     * 角色成员关系、组织归属与用户组成员关系（{@code delete} 不动它们）——
     * 这些边在账号被删期间是惰性的，但复活会**同时把它们激活**：
     * 一次"用回同一个登录名"的建号操作静默地把一批权限还给了那个人，
     * 而调用方以为自己只是新建了一个同名的空账号。租户域（{@code TenantService#create}）
     * 出于同一理由做同样的选择。
     */
    @Transactional
    public UserVO create(UserSaveRequest request) {
        Long tenantId = tenantService.resolveWriteTenant(request.getTenantId());
        // 存在还不够：停用的租户里建出来的账号永远登不进来（登录先拒 status != ENABLED
        // 的租户），却永久占着 uk_bw_auth_user_tenant_username。见 ADR-0051
        tenantService.assertAcceptsNewData(tenantId);
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw AuthResultCode.PARAM_INVALID.businessException("新增用户时口令不能为空");
        }
        // 建号只需三件事里的第一件：此刻没有"被替换下来的口令"（历史无从记起），
        // 也没有可重用的对象（防重用要拿当前或历史口令来比）。见 PasswordChanger#assertStrength
        passwordChanger.assertStrength(request.getPassword());
        if (userMapper.selectAnyByTenantAndUsername(tenantId, request.getUsername()) != null) {
            throw AuthResultCode.USERNAME_EXISTS.businessException();
        }
        // 寻址列（email / phone）的**判重口与写入口必须共用同一个归一值**（decisions/0072）：
        // 判重也是一次 eq（decisions/0062 把"判重"与"过滤"归在同一条判据下 —— 都是精确匹配），
        // 只归一其中一侧就会留下"存 13800138000、比『 13800138000 』"这种新的不对称，
        // 而它的症状是同一个手机号能建出两个账号。故这里只取一次值、两条路径都用它。
        String email = FilterValue.text(request.getEmail());
        String phone = FilterValue.text(request.getPhone());
        if (existsEmail(tenantId, email, null)) {
            throw AuthResultCode.USER_EMAIL_EXISTS.businessException();
        }
        if (existsPhone(tenantId, phone, null)) {
            throw AuthResultCode.USER_PHONE_EXISTS.businessException();
        }

        long now = System.currentTimeMillis();
        UserEntity entity = new UserEntity();
        entity.setTenantId(tenantId);
        entity.setUsername(request.getUsername());
        entity.setDisplayName(request.getDisplayName());
        entity.setEmail(email);
        entity.setPhone(phone);
        // 写入的这三列（哈希 / 算法标识 / 更新时刻）由 PasswordChanger 一处负责 ——
        // 建号走 insert、改密走 update，但"设口令要同时更新哪几列"只该有一个答案
        passwordChanger.assignNewPassword(entity, request.getPassword(), now);
        entity.setMustChangePassword(0);
        entity.setStatus(resolveStatus(request.getStatus()));
        entity.setFailedAttempts(0);
        entity.setMfaEnabled(0);
        userMapper.insert(entity);

        // 授予与建号在同一个事务里：建号成功而授予失败会留下一个"看起来建好了、
        // 却没有起步角色"的账号 —— 而它与"这个租户本来就没配默认角色"在界面上完全同形。
        // 因此这里不吞异常：整笔回滚，让失败可见。
        List<String> grantedDefaultRoles =
                roleMemberService.grantDefaultRolesTo(tenantId, entity.getId());
        if (!grantedDefaultRoles.isEmpty()) {
            // 不记审计（理由见 grantDefaultRolesTo 的 javadoc），但要在日志里留下"授了哪些"：
            // 审计只回答得了"谁建了这个号"，答不了"它一出生就带着哪些角色"
            log.info("建号并按租户默认角色自动授予 | userId={} | username={} | roles={}",
                    entity.getId(), entity.getUsername(), grantedDefaultRoles);
        }

        UserVO vo = toVO(entity);
        vo.setDefaultRoleNames(grantedDefaultRoles);
        return vo;
    }

    @Transactional
    public UserVO update(Long id, UserSaveRequest request) {
        UserEntity entity = requireUser(id);

        // 与 create 同源：归一值只取一次，判重与入库共用（decisions/0072）
        String email = FilterValue.text(request.getEmail());
        String phone = FilterValue.text(request.getPhone());
        if (existsEmail(entity.getTenantId(), email, id)) {
            throw AuthResultCode.USER_EMAIL_EXISTS.businessException();
        }
        if (existsPhone(entity.getTenantId(), phone, id)) {
            throw AuthResultCode.USER_PHONE_EXISTS.businessException();
        }

        // username 与 tenantId 不在修改项内：前者是登录凭据的一部分，
        // 后者决定数据归属 —— 两者变更都应是独立操作，不是一次普通字段更新。
        entity.setDisplayName(request.getDisplayName());
        entity.setEmail(email);
        entity.setPhone(phone);
        // 写入侧也要归一，与 create 同源（decisions/0071）。此前这里是把请求里的字面量
        // 直接存进去的，于是同一个字段在 create（走 resolveStatus）与 update 上宽容度不同：
        // 库里躺着一个 "enabled" 时，过滤 ?status=ENABLED 搜不到它 —— 而比这更重的是，
        // AuthService 判的是 Status.ENABLED.wireName().equals(user.getStatus())，
        // 于是**这个账号登不进来**：一次"保存成功"把账号锁在了门外。
        // 外层判空仍然要在（不传 status 表示"不改这一列"，不是"改成 ENABLED"），
        // 故先过 FilterValue.text() 再交给 resolveStatus。
        String requestedStatus = FilterValue.text(request.getStatus());
        if (requestedStatus != null) {
            entity.setStatus(resolveStatus(requestedStatus));
        }
        userMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * 管理员重置他人口令：重置后强制下次登录改密，**并撤销该账号的全部会话**。
     *
     * <p>后半句是本轮才对齐的效力（{@code docs/decisions/0046}）：重置通常意味着
     * "管理员认为这个账号出了问题"，此时若留着别人已经换到手的刷新令牌，
     * 重置就只是换了一把锁、门还开着 —— 一个握着刷新令牌的攻击者在那之后**仍然能续期**。
     * 它与自助改密走的是同一个 {@link PasswordChanger#change}，因此撤销也在同一处执行。
     */
    @Transactional
    public void resetPassword(Long id, String rawPassword) {
        UserEntity entity = requireUser(id);
        long now = System.currentTimeMillis();

        // 先设好"这次重置特有的列"，再交给 PasswordChanger —— 它末尾的 updateById
        // 会把它们与新口令一起落库。**顺序不能反**：changer 会立刻写库，
        // 之后设的字段要等调用方自己再补一次 update，而那时很容易忘。
        entity.setMustChangePassword(1);
        // 改密同时解锁并清零失败次数：管理员重置口令的场景，通常正是用户被锁住了
        entity.setFailedAttempts(0);
        entity.setLockedUntil(null);
        entity.setLockReason(null);
        // 退避层级一并归零：这是管理员介入后的"重新开始"，不该让用户继续背着之前的惩罚
        entity.setLockLevel(0);

        // 撤销该账号全部刷新令牌这一步不在这里，也不用在这里 —— 它在 change 的末尾，
        // 与"口令真的换掉了"同一次提交。**刻意不要再补一句**：本轮之前正是缺了它，
        // 而补法不是在这里加一行（下一处新入口照样会漏），是把它移到那个动作本身。
        passwordChanger.change(entity, rawPassword, PasswordChangeSource.ADMIN_RESET, now);
    }

    @Transactional
    public void delete(Long id) {
        UserEntity entity = requireUser(id);
        Long currentUserId = AuthContextHolder.currentUserId();
        if (currentUserId != null && currentUserId.equals(entity.getId())) {
            // 删除自己会让当前会话立刻失去主体，且可能把最后一个管理员删掉
            throw AuthResultCode.PARAM_INVALID.businessException("不能删除当前登录用户");
        }
        userMapper.deleteById(id);
    }

    private UserEntity requireUser(Long id) {
        UserEntity entity = id == null ? null : userMapper.selectById(id);
        if (entity == null) {
            throw AuthResultCode.USER_NOT_FOUND.businessException();
        }
        // 非平台级操作者够不到别家租户的用户 —— 与"这个用户不存在"**同码同文案**
        // （"用户不存在或不属于当前租户"），沿用 DataScopeService#resolve 的那条口径：
        // 403 等于承认"这个人存在，只是不给你看"，而"他存在"本身就是别人的信息。
        // 详情 / 修改 / 删除 / 重置口令 / 反查角色五条路径全部经这里（decisions/0074）
        tenantService.assertVisible(entity.getTenantId(), AuthResultCode.USER_NOT_FOUND);
        return entity;
    }

    /*
     * 登录名**没有**对应的 existsXxx：它必须"看得见软删行"，而 countBy 走的是
     * MyBatis-Plus 的自动查询（自动追加 deleted = 0），做不到这件事 ——
     * 见 UserMapper#selectAnyByTenantAndUsername 与 UserService#create。
     *
     * 邮箱与手机号继续用它，是因为那两列**没有唯一索引**：
     * 索引不挡它们，库里本来就不认为它们唯一，软删之后自然等于释放 ——
     * "软删算不算占着"这个问题只在被索引逼着的六个列上才需要回答（ADR-0056）。
     * 顺手把它们也改成"看得见软删行"是另一种错：那会让一次误删把邮箱永久锁住，
     * 而没有任何唯一约束要求这么做。
     *
     * <b>入参是已经过 FilterValue.text 归一的值，这两个方法自己不再判空白。</b>
     * 归一必须发生在调用方，且与"写进库里的那个值"是**同一个变量**（decisions/0072）——
     * 在这里再 trim 一遍就成了两份规矩，而"存 A、比 B"恰好是本片要消掉的那种不对称。
     * 归一之后"纯空白"已经变成 null，所以判空只剩 null 一种形态。
     */

    private boolean existsEmail(Long tenantId, String email, Long excludeId) {
        return email != null && countBy(tenantId, UserEntity::getEmail, email, excludeId) > 0;
    }

    private boolean existsPhone(Long tenantId, String phone, Long excludeId) {
        return phone != null && countBy(tenantId, UserEntity::getPhone, phone, excludeId) > 0;
    }

    private long countBy(Long tenantId, com.baomidou.mybatisplus.core.toolkit.support.SFunction<UserEntity, ?> column,
                         String value, Long excludeId) {
        Long count = userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getTenantId, tenantId)
                .eq(column, value)
                .ne(excludeId != null, UserEntity::getId, excludeId));
        return count == null ? 0L : count;
    }

    /*
     * 四个业务域各自的私有 resolveTenantId 已删除（decisions/0074）：租户解析连同
     * "非平台级只能管自己租户"这条隔离判定，一起收到了 TenantService 上
     * （resolveQueryTenant / resolveWriteTenant）—— 与 0050 把"租户存在性"从四个属主
     * 收到一处，是同一件事的下一步，而两个半片合成了一整条边界。
     *
     * 用户域那一份的历史值得留一句：它曾是四个属主里**唯一漏掉存在性校验**的一个，
     * 于是 POST /api/auth/users 带一个不存在的 tenantId 会**成功地**建出一个属于
     * 不存在租户的账号 —— 它永远登不进来（登录先按租户编码找租户）、永远匹配不上任何判定，
     * 却永久占着 uk_bw_auth_user_tenant_username 那个位置。DDL 里没有任何外键
     * （零 FOREIGN KEY），这件事只能由应用层负责（0050）。
     */

    private static String resolveStatus(String status) {
        Status parsed = Status.parse(status);
        return parsed == null ? Status.ENABLED.wireName() : parsed.wireName();
    }

    private static UserVO toVO(UserEntity entity) {
        UserVO vo = new UserVO();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setUsername(entity.getUsername());
        vo.setDisplayName(entity.getDisplayName());
        vo.setEmail(entity.getEmail());
        vo.setPhone(entity.getPhone());
        vo.setStatus(entity.getStatus());
        vo.setFailedAttempts(entity.getFailedAttempts());
        vo.setLockedUntil(entity.getLockedUntil());
        vo.setLockReason(entity.getLockReason());
        vo.setLockLevel(entity.getLockLevel());
        vo.setMustChangePassword(entity.getMustChangePassword());
        vo.setLastLoginAt(entity.getLastLoginAt());
        vo.setMfaEnabled(entity.getMfaEnabled());
        vo.setAvatarUrl(entity.getAvatarUrl());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

}
