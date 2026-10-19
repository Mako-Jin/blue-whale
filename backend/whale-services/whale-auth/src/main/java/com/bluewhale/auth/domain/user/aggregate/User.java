package com.bluewhale.auth.domain.user.aggregate;

import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * User 聚合根（对应 {@code sys_user}，REQ-ARCH-001 / T-DDD-4）。
 *
 * <p><b>不变量</b>：① {@code username} 非空且格式合法（其全局唯一性由
 * {@link com.bluewhale.auth.domain.user.repository.UserRepository#existsByUsername(String)}
 * 跨聚合校验）；② 口令必须为 BCrypt 密文（以 {@link EncryptedPassword} 承载，禁止明文）；
 * ③ {@code status ∈ {1 启用, 0 停用}}。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。</p>
 *
 * @author BlueWhale
 */
public class User {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;

    /** BCrypt 密文形态：以 {@code $2a$/$2b$/$2y$} 起始且带盐与散列内容 */
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$.+$");

    private final Long id;
    private final Username username;
    private final EncryptedPassword password;
    private String nickname;
    private String phone;
    private String email;
    private Long deptId;
    private int status;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验聚合不变量
     *
     * @param id 用户 ID（新建时为 {@code null}）
     * @param username 用户名值对象
     * @param password 口令密文值对象（必须为 BCrypt 密文）
     * @param nickname 昵称
     * @param phone 手机号
     * @param email 邮箱
     * @param deptId 所属部门 ID
     * @param status 状态（1 启用 / 0 停用）
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private User(Long id, Username username, EncryptedPassword password, String nickname, String phone,
                 String email, Long deptId, int status, LocalDateTime createTime, LocalDateTime updateTime) {
        if (username == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "用户名不能为空");
        }
        if (password == null || !BCRYPT_PATTERN.matcher(password.value()).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "口令必须为 BCrypt 密文");
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
        this.id = id;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.phone = phone;
        this.email = email;
        this.deptId = deptId;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：新建用户（无 ID），{@code status} 为空时默认启用
     *
     * @param username 用户名值对象
     * @param password 口令密文值对象（必须为 BCrypt 密文）
     * @param nickname 昵称
     * @param phone 手机号
     * @param email 邮箱
     * @param deptId 所属部门 ID
     * @param status 状态（1 启用 / 0 停用），为空时按启用处理
     * @return 新建的用户聚合根
     */
    public static User create(Username username, EncryptedPassword password, String nickname, String phone,
                              String email, Long deptId, Integer status) {
        LocalDateTime now = LocalDateTime.now();
        int resolved = status == null ? STATUS_ENABLED : status;
        return new User(null, username, password, nickname, phone, email, deptId, resolved, now, now);
    }

    /**
     * 重建方法：由持久化数据还原用户聚合根（{@code status} 为空时按停用处理，与既有登录判定一致）
     *
     * @param id 用户 ID
     * @param username 用户名值对象
     * @param password 口令密文值对象（必须为 BCrypt 密文）
     * @param nickname 昵称
     * @param phone 手机号
     * @param email 邮箱
     * @param deptId 所属部门 ID
     * @param status 状态（1 启用 / 0 停用），为空时按停用处理
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 还原的用户聚合根
     */
    public static User reconstitute(Long id, Username username, EncryptedPassword password, String nickname,
                                    String phone, String email, Long deptId, Integer status,
                                    LocalDateTime createTime, LocalDateTime updateTime) {
        int resolved = status == null ? STATUS_DISABLED : status;
        return new User(id, username, password, nickname, phone, email, deptId, resolved, createTime, updateTime);
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
     * 获取用户 ID
     *
     * @return 用户 ID（新建且未落库时为 {@code null}）
     */
    public Long getId() {
        return id;
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
