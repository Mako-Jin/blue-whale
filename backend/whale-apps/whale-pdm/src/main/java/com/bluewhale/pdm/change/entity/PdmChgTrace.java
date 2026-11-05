package com.bluewhale.pdm.change.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PDM 变更追溯（只追加）
 *
 * <p>记录 ECR/ECO/ECN 的每次状态流转与对象版本变化，供按零件/BOM/文档查询历史；
 * 服务层只提供新增与查询，不提供编辑/删除。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_chg_trace")
public class PdmChgTrace extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 业务类型：ECR/ECO/ECN */
    private String bizType;

    /** 业务ID */
    private Long bizId;

    /** 业务编号 */
    private String bizNo;

    /** 对象类型：DOCUMENT/BOM/PART */
    private String objectType;

    /** 对象ID */
    private Long objectId;

    /** 对象编码 */
    private String objectCode;

    /** 动作 */
    private String action;

    /** 变更前版本 */
    private String fromVersion;

    /** 变更后版本 */
    private String toVersion;

    /** 变更内容摘要 */
    private String content;

    /** 操作人 */
    private Long operatorId;

    /** 追溯时间 */
    private LocalDateTime traceTime;
}
