package com.bluewhale.sts.auth.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.auth.authentication.PasswordChanger;
import com.bluewhale.sts.auth.common.AuthResultCode;
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
 * <p><b>口令不在这里设。</b>强度校验、防重用、历史留痕三件事的实现在
 * {@link PasswordChanger} —— 本类（管理员重置）与 {@code AuthService}（自助改密）
 * 都只是它的调用方。错误码仍是 {@code 40003}（这次带的口令不合规）与
 * {@code 40914}（口令合法但撞了），分开是为了让前端能给出不同的引导，
 * 详见 {@link AuthResultCode}。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordChanger passwordChanger;

    public UserService(UserMapper userMapper, PasswordChanger passwordChanger) {
        this.userMapper = userMapper;
        this.passwordChanger = passwordChanger;
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
        // 建号只需三件事里的第一件：此刻没有"被替换下来的口令"（历史无从记起），
        // 也没有可重用的对象（防重用要拿当前或历史口令来比）。见 PasswordChanger#assertStrength
        passwordChanger.assertStrength(request.getPassword());
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
        // 写入的这三列（哈希 / 算法标识 / 更新时刻）由 PasswordChanger 一处负责 ——
        // 建号走 insert、改密走 update，但"设口令要同时更新哪几列"只该有一个答案
        passwordChanger.assignNewPassword(entity, request.getPassword(), now);
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
        vo.setMustChangePassword(entity.getMustChangePassword());
        vo.setLastLoginAt(entity.getLastLoginAt());
        vo.setMfaEnabled(entity.getMfaEnabled());
        vo.setAvatarUrl(entity.getAvatarUrl());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

}
