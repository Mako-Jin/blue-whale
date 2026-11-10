package com.bluewhale.contract.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 合同视图对象
 *
 * @author BlueWhale
 */
@Data
public class ContractVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（字符串返回，避免雪花精度丢失） */
    private String id;

    /** 合同编号 */
    private String code;

    /** 合同标题 */
    private String title;

    /** 合同类别 */
    private String category;

    /** 合同金额 */
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 负责人用户ID（字符串返回） */
    private String ownerId;

    /** 所属部门ID（字符串返回） */
    private String deptId;

    /** 状态 */
    private String status;

    /** 当前版本号 */
    private Integer currentVersion;

    /** 正文文件ID（字符串返回） */
    private String fileId;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 到期日期 */
    private LocalDate expireDate;

    /** 期满提醒：距到期天数（为空表示未设置到期日） */
    private Long daysToExpire;

    /** 归档条目ID（字符串返回） */
    private String archiveItemId;

    /** 弱关联业务类型 */
    private String bizType;

    /** 弱关联业务ID */
    private String bizId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 相对方列表（仅详情返回） */
    private List<ContractPartyVO> parties;

    /** 版本列表（仅详情返回） */
    private List<ContractVersionVO> versions;

    /** 流程记录（仅详情返回） */
    private List<ContractFlowVO> flows;
}
