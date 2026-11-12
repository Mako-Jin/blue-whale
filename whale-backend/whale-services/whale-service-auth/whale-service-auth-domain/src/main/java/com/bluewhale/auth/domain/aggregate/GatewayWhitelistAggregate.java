package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.common.domain.model.Entity;

/**
 * 网关免鉴权白名单聚合根（对应 {@code sys_gateway_whitelist}）。
 *
 * <p>白名单是权限中心控制平面的数据资产：网关按 60s 周期拉取启用项，
 * 与网关本地 {@code whale.gateway.white-list} 取并集后放行，远程拉取失败时仅用本地兜底。</p>
 *
 * <p><b>不变量</b>：① {@code pathPattern} 非空且以 {@code /} 开头；② {@code httpMethod}
 * 为空时视为 {@link #HTTP_METHOD_ANY}；③ 仅 {@code status=1} 参与下发；
 * ④ 同一 {@code (pathPattern, httpMethod, scope)} 只允许一条记录（仓储唯一键保证）。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。</p>
 *
 * @author BlueWhale
 */
public class GatewayWhitelistAggregate implements Entity<Long> {

    /** 作用域：平台级（当前唯一参与下发的范围） */
    public static final String SCOPE_PLATFORM = "PLATFORM";

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;

    /** 状态：停用 */
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
     * @param pathPattern 免鉴权路径（Ant 通配）
     * @param httpMethod HTTP 方法，空值归一为 {@link #HTTP_METHOD_ANY}
     * @param scope 作用域
     * @param status 状态
     * @param sortNo 排序号
     */
    private GatewayWhitelistAggregate(Long id, String pathPattern, String httpMethod, String scope,
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
     * @param pathPattern 免鉴权路径
     * @param httpMethod HTTP 方法
     * @param scope 作用域
     * @param status 状态
     * @param sortNo 排序号
     * @return 网关白名单聚合
     */
    public static GatewayWhitelistAggregate reconstitute(Long id, String pathPattern, String httpMethod,
                                                         String scope, Integer status, Integer sortNo) {
        return new GatewayWhitelistAggregate(id, pathPattern, httpMethod, scope,
                status == null ? STATUS_DISABLED : status, sortNo == null ? 0 : sortNo);
    }

    /**
     * 判断是否启用（仅启用项参与下发）
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
     * 获取免鉴权路径
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
     * @return 状态（1 启用 / 0 停用）
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
