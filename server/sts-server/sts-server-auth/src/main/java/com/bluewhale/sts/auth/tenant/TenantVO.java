package com.bluewhale.sts.auth.tenant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** 租户出参 */
@Getter
@Setter
public class TenantVO {

    private Long id;

    private String tenantCode;

    private String tenantName;

    private Long parentId;

    private Integer tenantLevel;

    private String status;

    private String description;

    private Integer allowRegister;

    private Integer allowAdd;

    private Long createTime;

    private Long updateTime;

    @Schema(description = "是否有子节点：0 否 1 是")
    private Integer hasChild;

}
