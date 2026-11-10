package com.bluewhale.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 流程代理规则实体，对应表 {@code process_delegate}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("process_delegate")
public class ProcessDelegate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流程编码（精确匹配） */
    private String definitionCode;

    /** 委托人账号 */
    private String fromAccount;

    /** 代理人账号 */
    private String toAccount;

    /** 生效开始时间 */
    private LocalDateTime startTime;

    /** 生效结束时间 */
    private LocalDateTime endTime;

    /** 状态：1 启用 / 0 停用 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}
