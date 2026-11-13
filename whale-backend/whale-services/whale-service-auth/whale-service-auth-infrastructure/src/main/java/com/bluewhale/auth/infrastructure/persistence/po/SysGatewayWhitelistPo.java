package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 网关白名单实体（对应 {@code sys_gateway_whitelist}）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_gateway_whitelist")
public class SysGatewayWhitelistPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 免鉴权路径（Ant 通配） */
    private String pathPattern;
    /** HTTP 方法：* 表示不限 */
    private String httpMethod;
    /** 作用域：PLATFORM 平台 / TENANT 租户 */
    private String scope;
    /** 租户 ID：0 平台级 / >0 绑定租户 */
    private Long tenantId;
    /** 来源：MIGRATION / MANUAL */
    private String source;
    /** 备注（放行原因） */
    private String remark;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    /** 排序号 */
    private Integer sortNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
