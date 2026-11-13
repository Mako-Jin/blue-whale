package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;

/**
 * 网关路由业务属性覆盖聚合根（对应 {@code sys_gateway_route_override}）。
 *
 * <p>路由定义（id/uri/predicates/filters/order）权威源为 Nacos、冷备为网关本地 YAML，
 * DB 不存定义；本聚合只承载业务属性覆盖：停用某条路由（全站 404）与生效时间窗口。
 * 网关经内部只读接口按 60s 周期拉取快照，在路由合并结果上过滤，不重启生效。</p>
 *
 * <p><b>不变量</b>：① {@code routeId} 非空（须与 Nacos/兜底路由 id 逐字一致，孤儿条目
 * 由网关忽略并计指标）；② {@code effectiveFrom}/{@code effectiveTo} 同时存在时
 * 起始不得晚于截止；③ 每次更新 {@code version} 自增，供网关快照变更识别。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。</p>
 *
 * @author BlueWhale
 */
public class GatewayRouteOverrideAggregate implements Entity<String> {

    /** 状态：启用（路由正常放行） */
    public static final int STATUS_ENABLED = 1;

    /** 状态：停用（路由全站 404） */
    public static final int STATUS_DISABLED = 0;

    /** 路由 ID 的路由节点（与 Nacos/兜底路由 id 逐字一致） */
    private final String routeId;

    /** 状态：1 启用 / 0 停用 */
    private final int status;

    /** 生效起始时间；{@code null} 立即生效 */
    private final LocalDateTime effectiveFrom;

    /** 生效截止时间；{@code null} 长期生效 */
    private final LocalDateTime effectiveTo;

    /** 版本号：每次更新 +1 */
    private final long version;

    /** 备注（启停原因） */
    private final String remark;

    private final LocalDateTime createTime;

    private final LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验不变量
     *
     * @param routeId       路由 ID
     * @param status        状态
     * @param effectiveFrom 生效起始时间
     * @param effectiveTo   生效截止时间
     * @param version       版本号
     * @param remark        备注
     * @param createTime    创建时间
     * @param updateTime    更新时间
     */
    private GatewayRouteOverrideAggregate(String routeId, int status, LocalDateTime effectiveFrom,
                                          LocalDateTime effectiveTo, long version, String remark,
                                          LocalDateTime createTime, LocalDateTime updateTime) {
        if (routeId == null || routeId.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "路由 ID 不能为空");
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
        if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "生效起始时间不能晚于截止时间");
        }
        if (version <= 0) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "版本号必须为正整数");
        }
        this.routeId = routeId.trim();
        this.status = status;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.version = version;
        this.remark = remark;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：管理端新增路由覆盖（版本号从 1 开始）
     *
     * @param routeId       路由 ID（与 Nacos/兜底路由 id 逐字一致）
     * @param status        状态，空值默认启用
     * @param effectiveFrom 生效起始时间，可空
     * @param effectiveTo   生效截止时间，可空
     * @param remark        备注（启停原因）
     * @return 新建的路由覆盖聚合
     */
    public static GatewayRouteOverrideAggregate create(String routeId, Integer status,
                                                       LocalDateTime effectiveFrom,
                                                       LocalDateTime effectiveTo, String remark) {
        LocalDateTime now = LocalDateTime.now();
        return new GatewayRouteOverrideAggregate(routeId,
                status == null ? STATUS_ENABLED : status, effectiveFrom, effectiveTo, 1L, remark, now, now);
    }

    /**
     * 由持久化数据重建聚合
     *
     * @param routeId       路由 ID
     * @param status        状态
     * @param effectiveFrom 生效起始时间
     * @param effectiveTo   生效截止时间
     * @param version       版本号
     * @param remark        备注
     * @param createTime    创建时间
     * @param updateTime    更新时间
     * @return 路由覆盖聚合
     */
    public static GatewayRouteOverrideAggregate reconstitute(String routeId, Integer status,
                                                             LocalDateTime effectiveFrom,
                                                             LocalDateTime effectiveTo, Long version,
                                                             String remark, LocalDateTime createTime,
                                                             LocalDateTime updateTime) {
        return new GatewayRouteOverrideAggregate(routeId, status == null ? STATUS_DISABLED : status,
                effectiveFrom, effectiveTo, version == null ? 1L : version, remark, createTime, updateTime);
    }

    /**
     * 更新可变字段（状态 / 生效窗口 / 备注），空值保持原值，版本号自增
     *
     * @param status        状态，空值保持原值
     * @param effectiveFrom 生效起始时间，空值保持原值
     * @param effectiveTo   生效截止时间，空值保持原值
     * @param remark        备注，空值保持原值
     * @return 更新后的路由覆盖聚合
     */
    public GatewayRouteOverrideAggregate applyUpdate(Integer status, LocalDateTime effectiveFrom,
                                                     LocalDateTime effectiveTo, String remark) {
        return new GatewayRouteOverrideAggregate(routeId,
                status == null ? this.status : status,
                effectiveFrom == null ? this.effectiveFrom : effectiveFrom,
                effectiveTo == null ? this.effectiveTo : effectiveTo,
                version + 1,
                remark == null ? this.remark : remark,
                createTime, LocalDateTime.now());
    }

    /**
     * 判断在给定时刻是否生效：启用且落在生效窗口内
     *
     * @param now 判定时刻
     * @return 生效返回 {@code true}
     */
    public boolean isEffectiveAt(LocalDateTime now) {
        if (status != STATUS_ENABLED) {
            return false;
        }
        if (effectiveFrom != null && now.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !now.isAfter(effectiveTo);
    }

    /**
     * 判断是否启用（不含时间窗口判定）
     *
     * @return 启用返回 {@code true}
     */
    public boolean isEnabled() {
        return status == STATUS_ENABLED;
    }

    /**
     * 获取实体主键（路由 ID）
     *
     * @return 路由 ID
     */
    @Override
    public String getId() {
        return routeId;
    }

    /**
     * 获取路由 ID
     *
     * @return 路由 ID
     */
    public String getRouteId() {
        return routeId;
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
     * 获取生效起始时间
     *
     * @return 生效起始时间；立即生效时为 {@code null}
     */
    public LocalDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    /**
     * 获取生效截止时间
     *
     * @return 生效截止时间；长期生效时为 {@code null}
     */
    public LocalDateTime getEffectiveTo() {
        return effectiveTo;
    }

    /**
     * 获取版本号
     *
     * @return 版本号
     */
    public long getVersion() {
        return version;
    }

    /**
     * 获取备注
     *
     * @return 备注（启停原因）
     */
    public String getRemark() {
        return remark;
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
