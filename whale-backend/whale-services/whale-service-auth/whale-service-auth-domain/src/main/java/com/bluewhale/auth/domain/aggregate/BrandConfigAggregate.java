package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 品牌配置聚合根（对应 {@code sys_brand_config}，权限中心扩展 M0）。
 *
 * <p><b>不变量</b>：① {@code targetType ∈ {PLATFORM, TENANT, COMPANY}}；
 * ② {@code targetId} 非空且不小于 0（PLATFORM 固定为 0）；③ {@code brandName} 非空；
 * ④ {@code primaryColor} 为空或为合法十六进制色值；⑤ 仅 {@code status=1} 的品牌参与登录页解析；
 * ⑥ 同一 {@code (targetType, targetId, domain)} 只允许一条记录（仓储唯一键保证）。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。</p>
 *
 * @author BlueWhale
 */
public class BrandConfigAggregate implements Entity<Long> {

    /** 作用域：平台级（targetId 固定 0） */
    public static final String TARGET_PLATFORM = "PLATFORM";
    /** 作用域：租户级 */
    public static final String TARGET_TENANT = "TENANT";
    /** 作用域：公司级 */
    public static final String TARGET_COMPANY = "COMPANY";

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;

    /** 十六进制色值：#RGB 或 #RRGGBB */
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");

    private final Long id;
    private final String targetType;
    private final Long targetId;
    private final String domain;
    private String brandName;
    private String logoUrl;
    private String loginTitle;
    private String institutionName;
    private String copyright;
    private String primaryColor;
    private String backgroundUrl;
    private int priority;
    private int status;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验聚合不变量
     *
     * @param id 主键（新建时为 {@code null}）
     * @param targetType 作用域类型
     * @param targetId 作用域 ID
     * @param domain 绑定域名（可空串）
     * @param brandName 品牌名称
     * @param logoUrl Logo 地址
     * @param loginTitle 登录页标题
     * @param institutionName 机构全称
     * @param copyright 版权信息
     * @param primaryColor 主题色
     * @param backgroundUrl 背景图地址
     * @param priority 解析优先级
     * @param status 状态
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private BrandConfigAggregate(Long id, String targetType, Long targetId, String domain, String brandName,
                                 String logoUrl, String loginTitle, String institutionName, String copyright,
                                 String primaryColor, String backgroundUrl, int priority, int status,
                                 LocalDateTime createTime, LocalDateTime updateTime) {
        validateTarget(targetType, targetId);
        validateBrandName(brandName);
        validateColor(primaryColor);
        validatePriority(priority);
        validateStatus(status);
        this.id = id;
        this.targetType = targetType;
        this.targetId = targetId;
        this.domain = domain == null ? "" : domain.trim();
        this.brandName = brandName;
        this.logoUrl = logoUrl;
        this.loginTitle = loginTitle;
        this.institutionName = institutionName;
        this.copyright = copyright;
        this.primaryColor = primaryColor;
        this.backgroundUrl = backgroundUrl;
        this.priority = priority;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 校验作用域类型与作用域 ID
     *
     * @param targetType 作用域类型
     * @param targetId 作用域 ID
     */
    private static void validateTarget(String targetType, Long targetId) {
        if (!TARGET_PLATFORM.equals(targetType) && !TARGET_TENANT.equals(targetType)
                && !TARGET_COMPANY.equals(targetType)) {
            throw new DomainException(ErrorCode.PARAM_ERROR,
                    "品牌作用域非法，仅支持 PLATFORM / TENANT / COMPANY");
        }
        if (targetId == null || targetId < 0) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "品牌作用域 ID 不能为空且不得为负数");
        }
        if (TARGET_PLATFORM.equals(targetType) && targetId != 0) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "平台级品牌的作用域 ID 必须为 0");
        }
    }

    /**
     * 校验品牌名称
     *
     * @param brandName 品牌名称
     */
    private static void validateBrandName(String brandName) {
        if (brandName == null || brandName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "品牌名称不能为空");
        }
    }

    /**
     * 校验主题色格式
     *
     * @param primaryColor 主题色
     */
    private static void validateColor(String primaryColor) {
        if (primaryColor != null && !primaryColor.isBlank() && !COLOR_PATTERN.matcher(primaryColor).matches()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "主题色必须为 #RGB 或 #RRGGBB 格式");
        }
    }

    /**
     * 校验解析优先级
     *
     * @param priority 解析优先级
     */
    private static void validatePriority(int priority) {
        if (priority < 0) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "解析优先级不得为负数");
        }
    }

    /**
     * 校验状态值
     *
     * @param status 状态
     */
    private static void validateStatus(int status) {
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
    }

    /**
     * 工厂方法：新建品牌配置（默认启用）
     *
     * @param targetType 作用域类型
     * @param targetId 作用域 ID
     * @param domain 绑定域名（可空）
     * @param brandName 品牌名称
     * @param logoUrl Logo 地址
     * @param loginTitle 登录页标题
     * @param institutionName 机构全称
     * @param copyright 版权信息
     * @param primaryColor 主题色
     * @param backgroundUrl 背景图地址
     * @param priority 解析优先级
     * @return 新建的品牌配置聚合
     */
    public static BrandConfigAggregate create(String targetType, Long targetId, String domain, String brandName,
                                              String logoUrl, String loginTitle, String institutionName,
                                              String copyright, String primaryColor, String backgroundUrl,
                                              int priority) {
        LocalDateTime now = LocalDateTime.now();
        return new BrandConfigAggregate(null, targetType, targetId, domain, brandName, logoUrl, loginTitle,
                institutionName, copyright, primaryColor, backgroundUrl, priority, STATUS_ENABLED, now, now);
    }

    /**
     * 由持久化数据重建聚合
     *
     * @param id 主键
     * @param targetType 作用域类型
     * @param targetId 作用域 ID
     * @param domain 绑定域名
     * @param brandName 品牌名称
     * @param logoUrl Logo 地址
     * @param loginTitle 登录页标题
     * @param institutionName 机构全称
     * @param copyright 版权信息
     * @param primaryColor 主题色
     * @param backgroundUrl 背景图地址
     * @param priority 解析优先级
     * @param status 状态
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 品牌配置聚合
     */
    public static BrandConfigAggregate reconstitute(Long id, String targetType, Long targetId, String domain,
                                                    String brandName, String logoUrl, String loginTitle,
                                                    String institutionName, String copyright, String primaryColor,
                                                    String backgroundUrl, Integer priority, Integer status,
                                                    LocalDateTime createTime, LocalDateTime updateTime) {
        return new BrandConfigAggregate(id, targetType, targetId, domain, brandName, logoUrl, loginTitle,
                institutionName, copyright, primaryColor, backgroundUrl,
                priority == null ? 0 : priority, status == null ? STATUS_DISABLED : status,
                createTime, updateTime);
    }

    /**
     * 行为：更新品牌主题与优先级
     *
     * @param brandName 品牌名称
     * @param logoUrl Logo 地址
     * @param loginTitle 登录页标题
     * @param institutionName 机构全称
     * @param copyright 版权信息
     * @param primaryColor 主题色
     * @param backgroundUrl 背景图地址
     * @param priority 解析优先级
     */
    public void updateTheme(String brandName, String logoUrl, String loginTitle, String institutionName,
                            String copyright, String primaryColor, String backgroundUrl, int priority) {
        validateBrandName(brandName);
        validateColor(primaryColor);
        validatePriority(priority);
        this.brandName = brandName;
        this.logoUrl = logoUrl;
        this.loginTitle = loginTitle;
        this.institutionName = institutionName;
        this.copyright = copyright;
        this.primaryColor = primaryColor;
        this.backgroundUrl = backgroundUrl;
        this.priority = priority;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：启用品牌
     */
    public void enable() {
        this.status = STATUS_ENABLED;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：停用品牌
     */
    public void disable() {
        this.status = STATUS_DISABLED;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 判断是否启用
     *
     * @return 启用返回 {@code true}
     */
    public boolean isEnabled() {
        return status == STATUS_ENABLED;
    }

    /**
     * 获取主键
     *
     * @return 主键
     */
    @Override
    public Long getId() {
        return id;
    }

    /**
     * 获取作用域类型
     *
     * @return 作用域类型
     */
    public String getTargetType() {
        return targetType;
    }

    /**
     * 获取作用域 ID
     *
     * @return 作用域 ID
     */
    public Long getTargetId() {
        return targetId;
    }

    /**
     * 获取绑定域名
     *
     * @return 绑定域名（无绑定时为空串）
     */
    public String getDomain() {
        return domain;
    }

    /**
     * 获取品牌名称
     *
     * @return 品牌名称
     */
    public String getBrandName() {
        return brandName;
    }

    /**
     * 获取 Logo 地址
     *
     * @return Logo 地址
     */
    public String getLogoUrl() {
        return logoUrl;
    }

    /**
     * 获取登录页标题
     *
     * @return 登录页标题
     */
    public String getLoginTitle() {
        return loginTitle;
    }

    /**
     * 获取机构全称
     *
     * @return 机构全称
     */
    public String getInstitutionName() {
        return institutionName;
    }

    /**
     * 获取版权信息
     *
     * @return 版权信息
     */
    public String getCopyright() {
        return copyright;
    }

    /**
     * 获取主题色
     *
     * @return 主题色
     */
    public String getPrimaryColor() {
        return primaryColor;
    }

    /**
     * 获取背景图地址
     *
     * @return 背景图地址
     */
    public String getBackgroundUrl() {
        return backgroundUrl;
    }

    /**
     * 获取解析优先级
     *
     * @return 解析优先级
     */
    public int getPriority() {
        return priority;
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
