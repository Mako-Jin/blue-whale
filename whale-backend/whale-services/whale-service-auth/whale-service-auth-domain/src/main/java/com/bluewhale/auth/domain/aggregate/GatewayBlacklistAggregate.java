package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 网关路径黑名单聚合根（对应 {@code sys_gateway_blacklist}）。
 *
 * <p><b>优先级不变量</b>：黑名单优先于白名单——网关先匹配黑名单，命中即拒绝（403），
 * 不再判断白名单。因此本表条目一旦启用，对应路径（含白名单路径）立即不可访问。</p>
 *
 * <p>黑名单是应急封禁手段：用于临时下线接口、拦截异常流量，变更后网关 60s 内生效，
 * 无需重启。仅 {@code status=1} 参与下发；{@code status=0} 表示封禁解除（保留记录便于审计）。</p>
 *
 * <p><b>不变量</b>：① {@code pathPattern} 非空且以 {@code /} 开头；② {@code httpMethod}
 * 为空时视为 {@link #HTTP_METHOD_ANY}；③ 仅 {@code status=1} 参与下发；
 * ④ 同一 {@code (pathPattern, httpMethod, scope)} 只允许一条记录（仓储唯一键保证）。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。</p>
 *
 * @author BlueWhale
 */
public class GatewayBlacklistAggregate implements Entity<Long> {

    /** 作用域：平台级（当前唯一参与下发的范围） */
    public static final String SCOPE_PLATFORM = "PLATFORM";

    /** 来源：管理端手工维护 */
    public static final String SOURCE_MANUAL = "MANUAL";

    /** 状态：生效 */
    public static final int STATUS_ENABLED = 1;

    /** 状态：解除 */
    public static final int STATUS_DISABLED = 0;

    /** HTTP 方法通配符：不限制请求方法 */
    public static final String HTTP_METHOD_ANY = "*";

    /** 路径前导字符：对外路径必须以 {@code /} 开头 */
    private static final String PATH_PREFIX = "/";

    private final Long id;
    private final String pathPattern;
    private final String httpMethod;
    private final String scope;
    private final String source;
    private final String remark;
    private final int status;
    private final int sortNo;
    private final LocalDateTime createTime;
    private final LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验不变量并归一化可空字段
     *
     * @param id 主键（新建时为 {@code null}）
     * @param pathPattern 封禁路径（Ant 通配）
     * @param httpMethod HTTP 方法，空值归一为 {@link #HTTP_METHOD_ANY}
     * @param scope 作用域
     * @param source 来源
     * @param remark 备注
     * @param status 状态
     * @param sortNo 排序号
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private GatewayBlacklistAggregate(Long id, String pathPattern, String httpMethod, String scope, String source,
                                      String remark, int status, int sortNo,
                                      LocalDateTime createTime, LocalDateTime updateTime) {
        if (pathPattern == null || pathPattern.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "黑名单路径不能为空");
        }
        String normalizedPath = pathPattern.trim();
        if (!normalizedPath.startsWith(PATH_PREFIX)) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "黑名单路径必须以 / 开头");
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（生效）/ 0（解除）");
        }
        this.id = id;
        this.pathPattern = normalizedPath;
        this.httpMethod = httpMethod == null || httpMethod.isBlank()
                ? HTTP_METHOD_ANY : httpMethod.trim().toUpperCase(Locale.ROOT);
        this.scope = scope == null || scope.isBlank() ? SCOPE_PLATFORM : scope.trim();
        this.source = source;
        this.remark = remark;
        this.status = status;
        this.sortNo = sortNo;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：管理端新增黑名单条目（来源固定 {@link #SOURCE_MANUAL}）
     *
     * @param pathPattern 封禁路径（Ant 通配）
     * @param httpMethod HTTP 方法，空值表示不限
     * @param status 状态，空值默认生效
     * @param sortNo 排序号，空值按 0 处理
     * @param remark 备注（封禁原因 / 工单号）
     * @return 新建的黑名单聚合
     */
    public static GatewayBlacklistAggregate create(String pathPattern, String httpMethod, Integer status,
                                                   Integer sortNo, String remark) {
        LocalDateTime now = LocalDateTime.now();
        return new GatewayBlacklistAggregate(null, pathPattern, httpMethod, SCOPE_PLATFORM, SOURCE_MANUAL, remark,
                status == null ? STATUS_ENABLED : status, sortNo == null ? 0 : sortNo, now, now);
    }

    /**
     * 由持久化数据重建聚合（兼容仅需匹配字段的查询场景）
     *
     * @param id 主键
     * @param pathPattern 封禁路径
     * @param httpMethod HTTP 方法
     * @param scope 作用域
     * @param status 状态
     * @param sortNo 排序号
     * @return 网关黑名单聚合
     */
    public static GatewayBlacklistAggregate reconstitute(Long id, String pathPattern, String httpMethod,
                                                         String scope, Integer status, Integer sortNo) {
        return reconstitute(id, pathPattern, httpMethod, scope, null, null, status, sortNo, null, null);
    }

    /**
     * 由持久化数据重建聚合（含管理端展示字段）
     *
     * @param id 主键
     * @param pathPattern 封禁路径
     * @param httpMethod HTTP 方法
     * @param scope 作用域
     * @param source 来源
     * @param remark 备注
     * @param status 状态
     * @param sortNo 排序号
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 网关黑名单聚合
     */
    public static GatewayBlacklistAggregate reconstitute(Long id, String pathPattern, String httpMethod,
                                                         String scope, String source, String remark, Integer status,
                                                         Integer sortNo, LocalDateTime createTime,
                                                         LocalDateTime updateTime) {
        return new GatewayBlacklistAggregate(id, pathPattern, httpMethod, scope, source, remark,
                status == null ? STATUS_DISABLED : status, sortNo == null ? 0 : sortNo, createTime, updateTime);
    }

    /**
     * 更新可变字段（路径 / 方法 / 状态 / 排序 / 备注），空值保持原值
     *
     * @param pathPattern 封禁路径，空值保持原值
     * @param httpMethod HTTP 方法，空值表示不限
     * @param status 状态，空值保持原值
     * @param sortNo 排序号，空值保持原值
     * @param remark 备注，空值保持原值
     * @return 更新后的黑名单聚合
     */
    public GatewayBlacklistAggregate applyUpdate(String pathPattern, String httpMethod, Integer status,
                                                 Integer sortNo, String remark) {
        return new GatewayBlacklistAggregate(id,
                pathPattern == null || pathPattern.isBlank() ? this.pathPattern : pathPattern,
                httpMethod, scope, source,
                remark == null ? this.remark : remark,
                status == null ? this.status : status,
                sortNo == null ? this.sortNo : sortNo,
                createTime, LocalDateTime.now());
    }

    /**
     * 判断是否生效（仅生效项参与下发）
     *
     * @return 生效返回 {@code true}
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
     * 获取封禁路径
     *
     * @return 路径模式（Ant 通配）
     */
    public String getPathPattern() {
        return pathPattern;
    }

    /**
     * 获取 HTTP 方法
     *
     * @return HTTP 方法，{@code *} 表示不限
     */
    public String getHttpMethod() {
        return httpMethod;
    }

    /**
     * 获取作用域
     *
     * @return 作用域
     */
    public String getScope() {
        return scope;
    }

    /**
     * 获取来源
     *
     * @return 来源（MIGRATION / MANUAL）
     */
    public String getSource() {
        return source;
    }

    /**
     * 获取备注
     *
     * @return 备注（封禁原因 / 工单号）
     */
    public String getRemark() {
        return remark;
    }

    /**
     * 获取状态
     *
     * @return 状态（1 生效 / 0 解除）
     */
    public int getStatus() {
        return status;
    }

    /**
     * 获取排序号
     *
     * @return 排序号
     */
    public int getSortNo() {
        return sortNo;
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
