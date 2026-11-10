package com.bluewhale.contract.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 合同版本视图对象
 *
 * @author BlueWhale
 */
@Data
public class ContractVersionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（字符串返回） */
    private String id;

    /** 合同ID（字符串返回） */
    private String contractId;

    /** 版本号 */
    private Integer versionNo;

    /** 正文文件ID（字符串返回） */
    private String fileId;

    /** 正文 SHA-256 */
    private String sha256;

    /** 变更说明 */
    private String changeNote;

    /** 版本状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
