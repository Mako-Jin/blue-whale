package com.bluewhale.auth.domain.user.aggregate;

import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * User 聚合根（对应 {@code sys_user}，REQ-ARCH-001 / T-DDD-4）。
 *
 * <p><b>不变量</b>：① {@code username} 非空且格式合法（其租户内唯一性由
 * {@link com.bluewhale.auth.domain.user.repository.UserRepository#existsByUsername(Long, String)}
 * 跨聚合校验）；② 口令必须为 BCrypt 密文（以 {@link EncryptedPassword} 承载，禁止明文）；
 * ③ {@code status ∈ {1 启用, 0 停用}}；④ 用户必须归属某个租户。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。</p>
 *
 * @author BlueWhale
 */
public class User implements Entity<Long> {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;

    /** BCrypt 密文形态：以 {@code $2a$/$2b$/$2y$} 起始且带盐与散列内容 */
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$.+$");

    private final Long id;
    private final Long tenantId;
    private final Username username;
    private EncryptedPassword password;
    /** 是否强制首登改密（1=是 / 0=否，T-03：出厂默认口令轮换后置位，改密成功后清除） */
    private boolean mustChangePassword;
    /** 最近一次改密时间（REQ-SEC-023：用于计算口令年龄与到期；存量用户回退为创建时间） */
    private LocalDateTime passwordUpdatedTime;
    private String nickname;
    private String phone;
    private String email;
    private String avatar;
    private Long deptId;
    private int status;
    private LocalDateTime lastLoginTime;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验聚合不变量
     *
     * @param id 用户 ID（新建时为 {@code null}）
     * @param tenantId 所属租户 ID
     * @param username 用户名值对象
     * @param password 口令密文值对象（必须为 BCrypt 密文）
     * @param nickname 昵称
     * @param phone 手机号
     * @param email 邮箱
     * @param avatar 头像地址
     * @param deptId 所属部门 ID
     * @param status 状态（1 启用 / 0 停用）
     * @param mustChangePassword 是否强制首登改密
     * @param passwordUpdatedTime 最近改密时间（为空时回退为创建时间，兼容存量数据）
     * @param lastLoginTime 最近登录时间
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private User(Long id, Long tenantId, Username username, EncryptedPassword password, String nickname,
                 String phone, String email, String avatar, Long deptId, int status, boolean mustChangePassword,
                 LocalDateTime passwordUpdatedTime, LocalDateTime lastLoginTime, LocalDateTime createTime,
                 LocalDateTime updateTime) {
        if (tenantId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "用户所属租户不能为空");
        }
        if (username == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "用户名不能为空");
        }
        if (password == null || !BCRYPT_PATTERN.matcher(password.value()).matches()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "口令必须为 BCrypt 密文");
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.phone = phone;
        this.email = email;
        this.avatar = avatar;
        this.deptId = deptId;
        this.status = status;
        this.mustChangePassword = mustChangePassword;
        this.passwordUpdatedTime = passwordUpdatedTime == null ? createTime : passwordUpdatedTime;
        this.lastLoginTime = lastLoginTime;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：新建用户（无 ID），{@code status} 为空时默认启用
     *
     * @param tenantId 所属租户 ID
     * @param username 用户名值对象
     * @param password 口令密文值对象（必须为 BCrypt 密文）
     * @param nickname 昵称
     * @param phone 手机号
     * @param email 邮箱
     * @param avatar 头像地址
     * @param deptId 所属部门 ID
     * @param status 状态（1 启用 / 0 停用），为空时按启用处理
     * @return 新建的用户聚合根
     */
    public static User create(Long tenantId, Username username, EncryptedPassword password, String nickname,
                              String phone, String email, String avatar, Long deptId, Integer status) {
        LocalDateTime now = LocalDateTime.now();
        int resolved = status == null ? STATUS_ENABLED : status;
        return new User(null, tenantId, username, password, nickname, phone, email, avatar, deptId, resolved,
                false, now, null, now, now);
    }

    /**
     * 工厂方法（兼容重载）：不含头像的新建用户
     *
     * @param tenantId 所属租户 ID
     * @param username 用户名值对象
     * @param password 口令密文值对象
     * @param nickname 昵称
     * @param phone 手机号
     * @param email 邮箱
     * @param deptId 所属部门 ID
     * @param status 状态（1 启用 / 0 停用），为空时按启用处理
     * @return 新建的用户聚合根
     */
    public static User create(Long tenantId, Username username, EncryptedPassword password, String nickname,
                              String phone, String email, Long deptId, Integer status) {
        return create(tenantId, username, password, nickname, phone, email, null, deptId, status);
    }

    /**
     * 重建方法：由持久化数据还原用户聚合根（{@code status} 为空时按停用处理，与既有登录判定一致）
     *
     * @param id 用户 ID
     * @param tenantId 所属租户 ID
     * @param username 用户名值对象
     * @param password 口令密文值对象（必须为 BCrypt 密文）
     * @param nickname 昵称
     * @param phone 手机号
     * @param email 邮箱
     * @param avatar 头像地址
     * @param deptId 所属部门 ID
     * @param status 状态（1 启用 / 0 停用），为空时按停用处理
     * @param lastLoginTime 最近登录时间
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 还原的用户聚合根
     */
    public static User reconstitute(Long id, Long tenantId, Username username, EncryptedPassword password,
                                    String nickname, String phone, String email, String avatar, Long deptId,
                                    Integer status, LocalDateTime lastLoginTime, LocalDateTime createTime,
                                    LocalDateTime updateTime) {
        return reconstitute(id, tenantId, username, password, nickname, phone, email, avatar, deptId, status,
                null, lastLoginTime, createTime, updateTime);
    }

    /**
     * 重建方法（全量重载）：由持久化数据还原用户聚合根，含强制首登改密标记（T-03）
     *
     * @param id 用户 ID
     * @param tenantId 所属租户 ID
     * @param username 用户名值对象
     * @param password 口令密文值对象（必须为 BCrypt 密文）
     * @param nickname 昵称
     * @param phone 手机号
     * @param email 邮箱
     * @param avatar 头像地址
     * @param deptId 所属部门 ID
     * @param status 状态（1 启用 / 0 停用），为空时按停用处理
     * @param mustChangePassword 是否强制首登改密（1=是 / 0=否 / null 按否处理）
     * @param lastLoginTime 最近登录时间
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 还原的用户聚合根
     */
    public static User reconstitute(Long id, Long tenantId, Username username, EncryptedPassword password,
                                    String nickname, String phone, String email, String avatar, Long deptId,
                                    Integer status, Integer mustChangePassword, LocalDateTime lastLoginTime,
                                    LocalDateTime createTime, LocalDateTime updateTime) {
        return reconstitute(id, tenantId, username, password, nickname, phone, email, avatar, deptId, status,
                mustChangePassword, null, lastLoginTime, createTime, updateTime);
    }

    /**
     * 重建方法（全量重载）：在原有字段基础上补充最近改密时间（REQ-SEC-023）
     *
     * @param id 用户 ID
     * @param tenantId 所属租户 ID
     * @param username 用户名值对象
     * @param password 口令密文值对象（必须为 BCrypt 密文）
     * @param nickname 昵称
     * @param phone 手机号
     * @param email 邮箱
     * @param avatar 头像地址
     * @param deptId 所属部门 ID
     * @param status 状态（1 启用 / 0 停用），为空时按停用处理
     * @param mustChangePassword 是否强制首登改密（1=是 / 0=否 / null 按否处理）
     * @param passwordUpdatedTime 最近改密时间；为空时回退为创建时间
     * @param lastLoginTime 最近登录时间
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 还原的用户聚合根
     */
    public static User reconstitute(Long id, Long tenantId, Username username, EncryptedPassword password,
                                    String nickname, String phone, String email, String avatar, Long deptId,
                                    Integer status, Integer mustChangePassword, LocalDateTime passwordUpdatedTime,
                                    LocalDateTime lastLoginTime, LocalDateTime createTime, LocalDateTime updateTime) {
        int resolved = status == null ? STATUS_DISABLED : status;
        boolean mustChange = mustChangePassword != null && mustChangePassword == 1;
        return new User(id, tenantId, username, password, nickname, phone, email, avatar, deptId, resolved,
                mustChange, passwordUpdatedTime, lastLoginTime, createTime, updateTime);
    }

    /**
     * 重建方法（兼容重载）：不含头像与最近登录时间的还原
     *
     * @param id 用户 ID
     * @param tenantId 所属租户 ID
     * @param username 用户名值对象
     * @param password 口令密文值对象
     * @param nickname 昵称
     * @param phone 手机号
     * @param email 邮箱
     * @param deptId 所属部门 ID
     * @param status 状态（1 启用 / 0 停用），为空时按停用处理
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 还原的用户聚合根
     */
    public static User reconstitute(Long id, Long tenantId, Username username, EncryptedPassword password,
                                    String nickname, String phone, String email, Long deptId, Integer status,
                                    LocalDateTime createTime, LocalDateTime updateTime) {
        return reconstitute(id, tenantId, username, password, nickname, phone, email, null, deptId, status,
                null, createTime, updateTime);
    }

    /**
     * 行为：停用当前用户
     */
    public void disable() {
        this.status = STATUS_DISABLED;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：启用当前用户
     */
    public void enable() {
        this.status = STATUS_ENABLED;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 判定当前用户是否启用
     *
     * @return 启用返回 true，停用返回 false
     */
    public boolean isEnabled() {
        return status == STATUS_ENABLED;
    }

    /**
     * 行为：变更用户资料（对应更新接口的昵称/手机号/邮箱/头像/部门赋值）
     *
     * @param nickname 昵称
     * @param phone 手机号
     * @param email 邮箱
     * @param avatar 头像地址
     * @param deptId 所属部门 ID
     */
    public void changeProfile(String nickname, String phone, String email, String avatar, Long deptId) {
        this.nickname = nickname;
        this.phone = phone;
        this.email = email;
        this.avatar = avatar;
        this.deptId = deptId;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：重置口令（明文必须先经 BCrypt 加密，此处只接受密文值对象）
     *
     * @param password 新口令密文值对象
     */
    public void changePassword(EncryptedPassword password) {
        changePassword(password, LocalDateTime.now());
    }

    /**
     * 行为：重置口令并记录改密时间（REQ-SEC-023：口令年龄从该时刻起算）
     *
     * @param password 新口令密文值对象
     * @param changedAt 改密生效时间（不得为空）
     */
    public void changePassword(EncryptedPassword password, LocalDateTime changedAt) {
        if (password == null || !BCRYPT_PATTERN.matcher(password.value()).matches()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "口令必须为 BCrypt 密文");
        }
        this.password = password;
        this.passwordUpdatedTime = changedAt;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 判定口令是否已到期（REQ-SEC-023）
     *
     * @param now 判定时刻
     * @param maxAgeDays 口令有效期（天）；小于等于 0 表示不强制到期
     * @return 已到期返回 true；改密时间缺失时返回 false（避免误锁存量账号）
     */
    public boolean isPasswordExpired(LocalDateTime now, int maxAgeDays) {
        if (maxAgeDays <= 0 || passwordUpdatedTime == null || now == null) {
            return false;
        }
        return passwordUpdatedTime.plusDays(maxAgeDays).isBefore(now)
                || passwordUpdatedTime.plusDays(maxAgeDays).isEqual(now);
    }

    /**
     * 计算距口令到期还剩多少天（REQ-SEC-023，供登录响应提示）
     *
     * @param now 判定时刻
     * @param maxAgeDays 口令有效期（天）；小于等于 0 或改密时间缺失时返回 -1（表示不适用）
     * @return 剩余天数（已到期为负数）；不适用返回 -1
     */
    public long passwordExpiresInDays(LocalDateTime now, int maxAgeDays) {
        if (maxAgeDays <= 0 || passwordUpdatedTime == null || now == null) {
            return -1L;
        }
        return Duration.between(now, passwordUpdatedTime.plusDays(maxAgeDays)).toDays();
    }

    /**
     * 行为：置强制首登改密标记（T-03：出厂默认口令被轮换后调用，用户下次登录需先改密）
     */
    public void markMustChangePassword() {
        this.mustChangePassword = true;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：清除强制首登改密标记（改密成功后调用）
     */
    public void clearMustChangePassword() {
        this.mustChangePassword = false;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 判定当前用户是否被强制首登改密
     *
     * @return 强制改密返回 true
     */
    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    /**
     * 行为：变更状态（对应启停接口的 {@code status} 赋值）
     *
     * @param status 新状态（1 启用 / 0 停用）
     */
    public void changeStatus(int status) {
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
        this.status = status;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：记录最近登录时间
     *
     * @param loginTime 登录时刻
     */
    public void recordLogin(LocalDateTime loginTime) {
        this.lastLoginTime = loginTime;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 获取用户 ID
     *
     * @return 用户 ID（新建且未落库时为 {@code null}）
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取所属租户 ID
     *
     * @return 所属租户 ID
     */
    public Long getTenantId() {
        return tenantId;
    }

    /**
     * 获取用户名值对象
     *
     * @return 用户名值对象
     */
    public Username getUsername() {
        return username;
    }

    /**
     * 获取口令密文值对象
     *
     * @return 口令密文值对象
     */
    public EncryptedPassword getPassword() {
        return password;
    }

    /**
     * 获取最近改密时间（REQ-SEC-023）
     *
     * @return 最近改密时间；存量数据缺失时回退为创建时间
     */
    public LocalDateTime getPasswordUpdatedTime() {
        return passwordUpdatedTime;
    }

    /**
     * 获取昵称
     *
     * @return 昵称
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * 获取手机号
     *
     * @return 手机号
     */
    public String getPhone() {
        return phone;
    }

    /**
     * 获取邮箱
     *
     * @return 邮箱
     */
    public String getEmail() {
        return email;
    }

    /**
     * 获取头像地址
     *
     * @return 头像地址
     */
    public String getAvatar() {
        return avatar;
    }

    /**
     * 获取最近登录时间
     *
     * @return 最近登录时间
     */
    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    /**
     * 获取所属部门 ID
     *
     * @return 所属部门 ID
     */
    public Long getDeptId() {
        return deptId;
    }

    /**
     * 获取状态
     *
     * @return 状态（1 启用 / 0 停用）
     */
    public int getStatus() {
        return status;
    }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
}
