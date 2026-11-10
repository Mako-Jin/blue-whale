package com.bluewhale.contract.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 证据链视图对象。
 *
 * @author BlueWhale
 */
@Data
public class EvidenceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 合同ID */
    private String contractId;

    /** 合同编号 */
    private String contractCode;

    /** 合同状态 */
    private String contractStatus;

    /** 签署任务列表（按顺序号升序） */
    private List<SignTaskVO> tasks;

    /** 签署记录列表（按签署时间升序） */
    private List<SignRecordVO> records;

    /** 链式哈希是否连续 */
    private Boolean chainValid;
}
