package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 网关黑名单实体（对应 {@code sys_gateway_blacklist}）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_gateway_blacklist")
public class SysGatewayBlacklistPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 封禁路径（Ant 通配） */
    private String pathPattern;
    /** HTTP 方法：* 表示不限 */
    private String httpMethod;
    /** 作用域：PLATFORM 平台 */
    private String scope;
    /** 来源：MANUAL / MIGRATION */
    private String source;
    /** 备注（封禁原因 / 工单号） */
    private String remark;
    /** 状态：1 生效 / 0 解除 */
    private Integer status;
    /** 排序号 */
    private Integer sortNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
