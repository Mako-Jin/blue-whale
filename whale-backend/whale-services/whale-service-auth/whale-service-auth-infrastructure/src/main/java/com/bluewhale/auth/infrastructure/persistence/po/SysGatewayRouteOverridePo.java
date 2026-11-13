package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 网关路由业务属性覆盖实体（对应 {@code sys_gateway_route_override}）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_gateway_route_override")
public class SysGatewayRouteOverridePo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 路由 ID（主键，与 Nacos/兜底路由 id 逐字一致） */
    private String routeId;
    /** 状态：1 启用 / 0 停用（停用=该路由全站 404） */
    private Integer enabled;
    /** 生效起始时间；NULL 立即生效 */
    private LocalDateTime effectiveFrom;
    /** 生效截止时间；NULL 长期生效 */
    private LocalDateTime effectiveTo;
    /** 版本号：每次更新 +1 */
    private Long version;
    /** 备注（启停原因） */
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
