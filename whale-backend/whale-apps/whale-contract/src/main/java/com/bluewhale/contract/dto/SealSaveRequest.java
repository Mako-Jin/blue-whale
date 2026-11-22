package com.bluewhale.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 印章保存请求。
 *
 * @author BlueWhale
 */
@Data
public class SealSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 印章名称 */
    @NotBlank(message = "{validation.contract.seal.name-required}")
    @Size(max = 128, message = "{validation.contract.seal.name-size}")
    private String name;

    /** 印章类型：COMPANY/CONTRACT/LEGAL/FINANCE */
    private String sealType;

    /** 印章图片文件ID */
    private Long imageFileId;

    /** 印章保管人用户ID */
    private Long keeperUserId;

    /** 有效期起 */
    private LocalDate validFrom;

    /** 有效期止 */
    private LocalDate validTo;

    /** 状态：ENABLED/DISABLED */
    private String status;
}
