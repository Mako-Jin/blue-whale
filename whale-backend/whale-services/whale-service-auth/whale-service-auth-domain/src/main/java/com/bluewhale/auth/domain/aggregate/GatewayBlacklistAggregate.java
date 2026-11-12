package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.common.domain.model.Entity;

/**
 * 网关路径黑名单聚合根（对应 {@code sys_gateway_blacklist}）。
 *
 * <p><b>优先级不变量</b>：黑名单优先于白名单——网关先匹配黑名单，命中即拒绝（403），
 * 不再判断白名单。因此本表条目一旦启用，对应路径（含白名单路径）立即不可访问。</p>
 *
 * <p>黑名单是应急封禁手段：用于临时下线接口、拦截异常流量，变更后网关 60s 内生效，
 * 无需重启。仅 {@code status=1} 参与下发；{@code status=0} 表示封禁解除（保留记录便于审计）。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。</p>
 *
 * @author BlueWhale
 */
public class GatewayBlacklistAggregate implements Entity<Long> {

    /** 作用域：平台级（当前唯一参与下发的范围） */
    public static final String SCOPE_PLATFORM = "PLATFORM";

    /** 状态：生效 */
    public static final int STATUS_ENABLED = 1;

    /** 状态：解除 */
    public static final int STATUS_DISABLED = 0;

    /** HTTP 方法通配符：不限制请求方法 */
    public static final String HTTP_METHOD_ANY = "*";

    private final Long id;
    private final String pathPattern;
    private final String httpMethod;
    private final String scope;
    private final int status;
    private final int sortNo;

    /**
     * 私有构造器：集中归一化可空字段
     *
     * @param id 主键
     * @param pathPattern 封禁路径（Ant 通配）
     * @param httpMethod HTTP 方法，空值归一为 {@link #HTTP_METHOD_ANY}
     * @param scope 作用域
     * @param status 状态
     * @param sortNo 排序号
     */
    private GatewayBlacklistAggregate(Long id, String pathPattern, String httpMethod, String scope,
                                      int status, int sortNo) {
        this.id = id;
        this.pathPattern = pathPattern;
        this.httpMethod = httpMethod == null || httpMethod.isBlank() ? HTTP_METHOD_ANY : httpMethod.trim();
        this.scope = scope;
        this.status = status;
        this.sortNo = sortNo;
    }

    /**
     * 由持久化数据重建聚合
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
        return new GatewayBlacklistAggregate(id, pathPattern, httpMethod, scope,
                status == null ? STATUS_DISABLED : status, sortNo == null ? 0 : sortNo);
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
}
