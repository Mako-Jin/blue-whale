package com.bluewhale.contract.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 合同修订（新建版本）请求
 *
 * @author BlueWhale
 */
@Data
public class ContractVersionSaveRequest {

    /** 新版本正文文件ID（可选，沿用当前正文时留空） */
    private Long fileId;

    /** 新版本正文 SHA-256（可选） */
    @Size(max = 64, message = "{validation.contract.contract-version.sha256-size}")
    private String sha256;

    /** 变更说明 */
    @Size(max = 255, message = "{validation.contract.contract-version.change-note-size}")
    private String changeNote;
}
