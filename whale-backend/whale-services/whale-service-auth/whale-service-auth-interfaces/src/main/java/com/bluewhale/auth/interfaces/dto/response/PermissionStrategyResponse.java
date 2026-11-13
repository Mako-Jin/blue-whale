package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限策略响应（权限中心扩展 M3）。
 *
 * @author BlueWhale
 */
@Data
public class PermissionStrategyResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String strategyCode;
    private String strategyName;
    private String strategyType;
    private List<Long> roleIds;
    private List<Long> resourceIds;
    private String conditionJson;
    private boolean defaultStrategy;
    private Integer priority;
    private Integer status;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
