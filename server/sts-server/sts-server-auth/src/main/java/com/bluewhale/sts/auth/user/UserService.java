package com.bluewhale.sts.auth.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.auth.authentication.PasswordHistoryEntity;
import com.bluewhale.sts.auth.authentication.PasswordHistoryMapper;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.auth.common.PasswordPolicy;
import com.bluewhale.sts.auth.common.codec.PasswordHasher;
import com.bluewhale.sts.auth.common.enums.PasswordChangeSource;
import com.bluewhale.sts.auth.common.enums.Status;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import com.bluewhale.sts.common.basic.model.PageResult;
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
 * <p><b>口令的两道关：</b>设置口令（建号 / 重置）先过 {@link PasswordPolicy} 的形态校验，
 * 重置时再过"不得与当前或最近若干个历史口令重复"。
 * 前者的错误码是 {@code 40003}（这次带的口令不合规），后者是 {@code 40914}（口令合法但撞了）——
 * 分开是为了让前端能给出不同的引导，详见 {@link AuthResultCode}。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHistoryMapper passwordHistoryMapper;

    public UserService(UserMapper userMapper,
                       PasswordHasher passwordHasher,
                       PasswordPolicy passwordPolicy,
                       PasswordHistoryMapper passwordHistoryMapper) {
        this.userMapper = userMapper;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.passwordHistoryMapper = passwordHistoryMapper;
    }

    @Transactional(readOnly = true)
    public PageResult<UserVO> page(UserQuery query) {
        Long tenantId = resolveTenantId(query.getTenantId());
        var wrapper = Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getTenantId, tenantId)
                .and(query.getKeyword() != null && !query.getKeyword().isBlank(), w -> w
                        .like(UserEntity::getUsername, query.getKeyword())
                        .or().like(UserEntity::getDisplayName, query.getKeyword())
                        .or().like(UserEntity::getEmail, query.getKeyword()))
                .eq(query.getStatus() != null && !query.getStatus().isBlank(),
                        UserEntity::getStatus, query.getStatus())
                .orderByDesc(UserEntity::getId);

        Page<UserEntity> page = userMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        List<UserVO> records = page.getRecords().stream().map(UserService::toVO).toList();
        return PageResult.of(records, page.getTotal(), query.getPage(), query.getSize());
    }

    @Transactional(readOnly = true)
    public UserVO detail(Long id) {
        return toVO(requireUser(id));
    }

    @Transactional
    public UserVO create(UserSaveRequest request) {
        Long tenantId = resolveTenantId(request.getTenantId());
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw AuthResultCode.PARAM_INVALID.businessException("新增用户时口令不能为空");
        }
        assertPasswordStrength(request.getPassword());
        if (existsUsername(tenantId, request.getUsername(), null)) {
            throw AuthResultCode.USERNAME_EXISTS.businessException();
        }
        if (existsEmail(tenantId, request.getEmail(), null)) {
            throw AuthResultCode.USER_EMAIL_EXISTS.businessException();
        }
        if (existsPhone(tenantId, request.getPhone(), null)) {
            throw AuthResultCode.USER_PHONE_EXISTS.businessException();
        }

        long now = System.currentTimeMillis();
        UserEntity entity = new UserEntity();
        entity.setTenantId(tenantId);
        entity.setUsername(request.getUsername());
        entity.setDisplayName(request.getDisplayName());
        entity.setEmail(blankToNull(request.getEmail()));
        entity.setPhone(blankToNull(request.getPhone()));
        entity.setPasswordHash(passwordHasher.hash(request.getPassword()));
        entity.setPasswordAlgorithm(passwordHasher.algorithmId());
        entity.setPasswordUpdatedAt(now);
        entity.setMustChangePassword(0);
        entity.setStatus(resolveStatus(request.getStatus()));
        entity.setFailedAttempts(0);
        entity.setMfaEnabled(0);
        userMapper.insert(entity);
        return toVO(entity);
    }

    @Transactional
    public UserVO update(Long id, UserSaveRequest request) {
        UserEntity entity = requireUser(id);

        if (existsEmail(entity.getTenantId(), request.getEmail(), id)) {
            throw AuthResultCode.USER_EMAIL_EXISTS.businessException();
        }
        if (existsPhone(entity.getTenantId(), request.getPhone(), id)) {
            throw AuthResultCode.USER_PHONE_EXISTS.businessException();
        }

        // username 与 tenantId 不在修改项内：前者是登录凭据的一部分，
        // 后者决定数据归属 —— 两者变更都应是独立操作，不是一次普通字段更新。
        entity.setDisplayName(request.getDisplayName());
        entity.setEmail(blankToNull(request.getEmail()));
        entity.setPhone(blankToNull(request.getPhone()));
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            entity.setStatus(request.getStatus());
        }
        userMapper.updateById(entity);
        return toVO(entity);
    }

    /** 管理员重置他人口令：重置后强制下次登录改密 */
    @Transactional
    public void resetPassword(Long id, String rawPassword) {
        UserEntity entity = requireUser(id);
        long now = System.currentTimeMillis();

        assertPasswordStrength(rawPassword);
        assertNotReused(entity, rawPassword);

        // 被替换掉的口令进历史 —— 它从此"成为过去"，而"过去用过的不能再回来"正是
        // 防重用检查要保证的事。建号时不写历史：那时没有"被替换下来的口令"，
        // 当前口令由 user.password_hash 自己承担（校验时两者都看，见 assertNotReused）。
        if (entity.getPasswordHash() != null) {
            recordHistory(entity, entity.getPasswordHash(), PasswordChangeSource.ADMIN_RESET, now);
        }

        entity.setPasswordHash(passwordHasher.hash(rawPassword));
        entity.setPasswordAlgorithm(passwordHasher.algorithmId());
        entity.setPasswordUpdatedAt(now);
        entity.setMustChangePassword(1);
        // 改密同时解锁并清零失败次数：管理员重置口令的场景，通常正是用户被锁住了
        entity.setFailedAttempts(0);
        entity.setLockedUntil(null);
        entity.setLockReason(null);
        // 退避层级一并归零：这是管理员介入后的"重新开始"，不该让用户继续背着之前的惩罚
        entity.setLockLevel(0);
        userMapper.updateById(entity);
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

    // ==================== 口令强度与防重用 ====================

    /** 形态校验：长度与字符类别。失败时把**缺哪几项**与**完整要求**一起回给调用方 */
    private void assertPasswordStrength(String rawPassword) {
        List<String> missing = passwordPolicy.violations(rawPassword);
        if (missing.isEmpty()) {
            return;
        }
        throw AuthResultCode.PASSWORD_POLICY_VIOLATION.businessException(
                "口令强度不足：" + String.join("、", missing) + "（要求：" + passwordPolicy.describe() + "）");
    }

    /**
     * 防重用：新口令既不得等于**当前口令**，也不得等于**最近若干个历史口令**。
     *
     * <p><b>当前口令必须单独比对</b>，因为它不在历史表里 ——
     * 历史记的是"被替换下来的那些"，而当前这一个还没被替换过。
     * 漏掉这一步的后果很具体：连续重置两次、第二次填回原来的口令会静默成功。
     */
    private void assertNotReused(UserEntity user, String rawPassword) {
        if (user.getPasswordHash() != null && passwordHasher.matches(rawPassword, user.getPasswordHash())) {
            throw AuthResultCode.PASSWORD_REUSED.businessException();
        }
        boolean reused = recentHistories(user.getId()).stream()
                .anyMatch(history -> passwordHasher.matches(rawPassword, history.getPasswordHash()));
        if (reused) {
            throw AuthResultCode.PASSWORD_REUSED.businessException();
        }
    }

    /** 取该用户最近 N 条历史口令（N = 配置 {@code historyCount}；为 0 时返回空列表，即关闭该检查） */
    private List<PasswordHistoryEntity> recentHistories(Long userId) {
        int count = passwordPolicy.historyCount();
        if (count <= 0) {
            return List.of();
        }
        // searchCount=false：只要数据不要总数，避免每次改密都多跑一条 COUNT
        Page<PasswordHistoryEntity> page = new Page<>(1, count, false);
        return passwordHistoryMapper.selectList(page, Wrappers.<PasswordHistoryEntity>lambdaQuery()
                .eq(PasswordHistoryEntity::getUserId, userId)
                .orderByDesc(PasswordHistoryEntity::getChangeTime)
                .orderByDesc(PasswordHistoryEntity::getId));
    }

    /** 把一条口令哈希记入历史，并裁掉超出保留数的旧记录 */
    private void recordHistory(UserEntity user, String passwordHash, PasswordChangeSource source, long now) {
        PasswordHistoryEntity history = new PasswordHistoryEntity();
        history.setTenantId(user.getTenantId());
        history.setUserId(user.getId());
        history.setPasswordHash(passwordHash);
        history.setPasswordAlgorithm(user.getPasswordAlgorithm());
        history.setChangeSource(source.wireName());
        history.setChangeTime(now);
        passwordHistoryMapper.insert(history);
        trimHistory(user.getId());
    }

    /**
     * 只保留最近 N 条。
     *
     * <p><b>裁剪按"条数"而不是按"时间"：</b>这项功能关心的是"要防几次重用"，
     * 条数直接对应它；按时间裁剪时表的规模取决于"多久清一次"，与检查能覆盖的范围脱钩 ——
     * 长期不改密的账号会攒下无限多行，而频繁改密的账号可能只剩两行。
     * 参照实现 {@code SystemTerminator} 用的是时间过期，这是本仓不采纳的一处（见 ADR-0020）。
     */
    private void trimHistory(Long userId) {
        int keep = passwordPolicy.historyCount();
        if (keep <= 0) {
            return;
        }
        List<PasswordHistoryEntity> ordered = passwordHistoryMapper.selectList(
                Wrappers.<PasswordHistoryEntity>lambdaQuery()
                        .eq(PasswordHistoryEntity::getUserId, userId)
                        .orderByDesc(PasswordHistoryEntity::getId));
        if (ordered.size() <= keep) {
            return;
        }
        List<Long> keepIds = ordered.subList(0, keep).stream()
                .map(PasswordHistoryEntity::getId)
                .toList();
        passwordHistoryMapper.delete(Wrappers.<PasswordHistoryEntity>lambdaQuery()
                .eq(PasswordHistoryEntity::getUserId, userId)
                .notIn(PasswordHistoryEntity::getId, keepIds));
    }

    private UserEntity requireUser(Long id) {
        UserEntity entity = id == null ? null : userMapper.selectById(id);
        if (entity == null) {
            throw AuthResultCode.USER_NOT_FOUND.businessException();
        }
        return entity;
    }

    private boolean existsUsername(Long tenantId, String username, Long excludeId) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return countBy(tenantId, UserEntity::getUsername, username, excludeId) > 0;
    }

    private boolean existsEmail(Long tenantId, String email, Long excludeId) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return countBy(tenantId, UserEntity::getEmail, email, excludeId) > 0;
    }

    private boolean existsPhone(Long tenantId, String phone, Long excludeId) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        return countBy(tenantId, UserEntity::getPhone, phone, excludeId) > 0;
    }

    private long countBy(Long tenantId, com.baomidou.mybatisplus.core.toolkit.support.SFunction<UserEntity, ?> column,
                         String value, Long excludeId) {
        Long count = userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getTenantId, tenantId)
                .eq(column, value)
                .ne(excludeId != null, UserEntity::getId, excludeId));
        return count == null ? 0L : count;
    }

    private static Long resolveTenantId(Long requested) {
        if (requested != null) {
            return requested;
        }
        Long current = AuthContextHolder.currentTenantId();
        if (current == null) {
            throw AuthResultCode.PARAM_INVALID.businessException("无法确定租户，请显式指定 tenantId");
        }
        return current;
    }

    private static String resolveStatus(String status) {
        Status parsed = Status.parse(status);
        return parsed == null ? Status.ENABLED.wireName() : parsed.wireName();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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
        vo.setLastLoginAt(entity.getLastLoginAt());
        vo.setMfaEnabled(entity.getMfaEnabled());
        vo.setAvatarUrl(entity.getAvatarUrl());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

}
