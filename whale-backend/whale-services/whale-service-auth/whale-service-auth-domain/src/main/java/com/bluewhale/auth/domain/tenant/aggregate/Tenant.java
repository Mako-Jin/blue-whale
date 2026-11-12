package com.bluewhale.auth.domain.tenant.aggregate;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Tenant 聚合根（对应 {@code sys_tenant}，Wave 5.54 Phase 1）。
 *
 * <p><b>不变量</b>：① {@code tenantCode} 非空且仅允许字母/数字/下划线/中划线；② {@code tenantName} 非空；
 * ③ {@code tenantType ∈ {PLATFORM, TENANT}}；④ 仅 {@code status=1} 且未过期的租户可用于登录与签发令牌。</p>
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。</p>
 *
 * <p><b>时间口径</b>：本聚合所有时间字段（{@code expireTime}/{@code createTime}/{@code updateTime}）
 * 统一按 {@link TenantExpiryClock}（UTC）取当前时间，避免到期判定与审计时间口径不一致。</p>
 *
 * <p>选型说明：本类实现 {@link Entity} 而非继承 {@code AbstractAggregate}。后者相对
 * {@code Entity} 只多两项东西——领域事件收集/发布（{@code registerEvent}/{@code publishEvents}）
 * 与「主键由父构造器绑定、子类不可自行维护」的约束。权限中心各聚合（Tenant/User/Role/Menu/
 * Dept/Permission）全都不发布领域事件，继承只会得到一套永不使用的空壳，并把 id 变成父类字段。
 * 等真正需要事件驱动（如租户停用需广播缓存失效）时，再统一改为继承并补事件登记。</p>
 *
 * @author BlueWhale
 */
public class Tenant implements Entity<Long> {

    /** 租户类型：平台运营租户（仅该类租户可管理租户清单） */
    public static final String TYPE_PLATFORM = "PLATFORM";
    /** 租户类型：普通业务租户 */
    public static final String TYPE_TENANT = "TENANT";
    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;
    /** 平台租户固定 ID：存量数据统一归属该租户 */
    public static final long PLATFORM_TENANT_ID = 1L;

    /** 租户编码格式：2–64 位字母/数字/下划线/中划线 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{2,64}$");

    private final Long id;
    private final String tenantCode;
    private String tenantName;
    private final String tenantType;
    private int status;
    private LocalDateTime expireTime;
    private String quotaJson;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验聚合不变量
     *
     * @param id 租户 ID（新建时为 {@code null}）
     * @param tenantCode 租户编码
     * @param tenantName 租户名称
     * @param tenantType 租户类型（PLATFORM / TENANT）
     * @param status 状态（1 启用 / 0 停用）
     * @param expireTime 到期时间（{@code null} 表示长期有效）
     * @param quotaJson 配额 JSON
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private Tenant(Long id, String tenantCode, String tenantName, String tenantType, int status,
                   LocalDateTime expireTime, String quotaJson, LocalDateTime createTime,
                   LocalDateTime updateTime) {
        if (tenantCode == null || !CODE_PATTERN.matcher(tenantCode).matches()) {
            throw new DomainException(ErrorCode.PARAM_ERROR,
                    "租户编码非法，仅支持 2–64 位字母、数字、下划线或中划线");
        }
        if (tenantName == null || tenantName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "租户名称不能为空");
        }
        if (!TYPE_PLATFORM.equals(tenantType) && !TYPE_TENANT.equals(tenantType)) {
            throw new DomainException(ErrorCode.PARAM_ERROR,
                    "租户类型非法，仅支持 PLATFORM（平台）/ TENANT（业务租户）");
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
        this.id = id;
        this.tenantCode = tenantCode;
        this.tenantName = tenantName;
        this.tenantType = tenantType;
        this.status = status;
        this.expireTime = expireTime;
        this.quotaJson = quotaJson;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：新建业务租户（默认启用，固定 {@code tenantType=TENANT}）
     *
     * <p>平台运营租户（{@code PLATFORM}，ID 固定为 {@link #PLATFORM_TENANT_ID}）仅通过种子数据创建，
     * 不支持程序化创建。</p>
     *
     * @param tenantCode 租户编码
     * @param tenantName 租户名称
     * @param expireTime 到期时间（{@code null} 表示长期有效，UTC 口径）
     * @param quotaJson 配额 JSON
     * @return 新建的租户聚合
     */
    public static Tenant create(String tenantCode, String tenantName, LocalDateTime expireTime, String quotaJson) {
        LocalDateTime now = TenantExpiryClock.now();
        return new Tenant(null, tenantCode, tenantName, TYPE_TENANT, STATUS_ENABLED, expireTime, quotaJson,
                now, now);
    }

    /**
     * 由持久化数据重建聚合
     *
     * <p>注意：重建与 {@link #create} 走同一私有构造器，同样执行不变量校验
     * （{@code tenantCode} 格式、{@code tenantType} 合法性等）；存量脏数据将在此处暴露，
     * 而不是被静默放过。</p>
     *
     * @param id 租户 ID
     * @param tenantCode 租户编码
     * @param tenantName 租户名称
     * @param tenantType 租户类型，为空时按 TENANT 处理
     * @param status 状态（1 启用 / 0 停用），{@code null} 按停用处理
     * @param expireTime 到期时间
     * @param quotaJson 配额 JSON
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 租户聚合
     */
    public static Tenant reconstitute(Long id, String tenantCode, String tenantName, String tenantType,
                                      Integer status, LocalDateTime expireTime, String quotaJson,
                                      LocalDateTime createTime, LocalDateTime updateTime) {
        String resolvedType = tenantType == null || tenantType.isBlank() ? TYPE_TENANT : tenantType;
        return new Tenant(id, tenantCode, tenantName, resolvedType,
                status == null ? STATUS_DISABLED : status, expireTime, quotaJson, createTime, updateTime);
    }

    /**
     * 行为：重命名租户
     *
     * @param tenantName 新租户名称
     */
    public void rename(String tenantName) {
        if (tenantName == null || tenantName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "租户名称不能为空");
        }
        this.tenantName = tenantName;
        this.updateTime = TenantExpiryClock.now();
    }

    /**
     * 行为：变更配额 JSON
     *
     * @param quotaJson 配额 JSON
     */
    public void changeQuota(String quotaJson) {
        this.quotaJson = quotaJson;
        this.updateTime = TenantExpiryClock.now();
    }

    /**
     * 行为：续期
     *
     * @param expireTime 新到期时间，必须晚于当前时间
     */
    public void renew(LocalDateTime expireTime) {
        if (expireTime == null || !expireTime.isAfter(TenantExpiryClock.now())) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "续期时间必须晚于当前时间");
        }
        this.expireTime = expireTime;
        this.updateTime = TenantExpiryClock.now();
    }

    /**
     * 行为：启用租户
     */
    public void enable() {
        this.status = STATUS_ENABLED;
        this.updateTime = TenantExpiryClock.now();
    }

    /**
     * 行为：停用租户
     */
    public void disable() {
        this.status = STATUS_DISABLED;
        this.updateTime = TenantExpiryClock.now();
    }

    /**
     * 判断租户是否启用
     *
     * @return 启用返回 {@code true}
     */
    public boolean isEnabled() {
        return status == STATUS_ENABLED;
    }

    /**
     * 判断租户在指定时间是否已过期
     *
     * @param now 判定时间
     * @return 已过期返回 {@code true}
     */
    public boolean isExpiredAt(LocalDateTime now) {
        return expireTime != null && !expireTime.isAfter(now);
    }

    /**
     * 判断租户在指定时间是否可用（启用且未过期）
     *
     * @param now 判定时间
     * @return 可用返回 {@code true}
     */
    public boolean isAvailableAt(LocalDateTime now) {
        return isEnabled() && !isExpiredAt(now);
    }

    /**
     * 判断是否为平台运营租户
     *
     * @return 平台租户返回 {@code true}
     */
    public boolean isPlatform() {
        return TYPE_PLATFORM.equals(tenantType);
    }

    /**
     * 获取租户 ID
     *
     * @return 租户 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取租户编码
     *
     * @return 租户编码
     */
    public String getTenantCode() {
        return tenantCode;
    }

    /**
     * 获取租户名称
     *
     * @return 租户名称
     */
    public String getTenantName() {
        return tenantName;
    }

    /**
     * 获取租户类型
     *
     * @return 租户类型（PLATFORM / TENANT）
     */
    public String getTenantType() {
        return tenantType;
    }

    /**
     * 获取租户状态
     *
     * @return 状态（1 启用 / 0 停用）
     */
    public int getStatus() {
        return status;
    }

    /**
     * 获取到期时间
     *
     * @return 到期时间；长期有效时返回 {@code null}
     */
    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    /**
     * 获取配额 JSON
     *
     * @return 配额 JSON
     */
    public String getQuotaJson() {
        return quotaJson;
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
