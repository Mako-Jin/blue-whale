package com.bluewhale.contract.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 合同流程记录视图对象
 *
 * @author BlueWhale
 */
@Data
public class ContractFlowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（字符串返回） */
    private String id;

    /** 合同ID（字符串返回） */
    private String contractId;

    /** 版本ID（字符串返回） */
    private String versionId;

    /** 工作流实例ID（字符串返回） */
    private String processInstanceId;

    /** 流程定义ID（字符串返回） */
    private String definitionId;

    /** 阶段 */
    private String stage;

    /** 状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
