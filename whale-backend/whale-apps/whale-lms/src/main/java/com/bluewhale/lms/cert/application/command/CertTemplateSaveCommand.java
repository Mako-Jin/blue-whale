package com.bluewhale.lms.cert.application.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 证书模板保存命令。
 *
 * @author BlueWhale
 */
@Data
public class CertTemplateSaveCommand {

    /** 模板编码（租户内唯一，保存时统一转大写） */
    @NotBlank(message = "模板编码不能为空")
    @Size(max = 64, message = "模板编码不得超过 64 字符")
    private String templateCode;

    /** 证书名称 */
    @NotBlank(message = "证书名称不能为空")
    @Size(max = 128, message = "证书名称不得超过 128 字符")
    private String name;

    /** 证书类别 */
    @Size(max = 32, message = "证书类别不得超过 32 字符")
    private String category;

    /** 发证依据：MANUAL / COURSE / EXAM / TRAINING */
    private String issueBasis;

    /** 有效期月数（0 为长期有效） */
    @Min(value = 0, message = "有效期月数不能为负")
    @Max(value = 600, message = "有效期月数不得超过 600")
    private Integer validMonths;

    /** 编号前缀（大写字母数字，2–16 位） */
    private String certPrefix;

    /** 证书正文模板 */
    @Size(max = 4000, message = "证书正文不得超过 4000 字符")
    private String content;

    /** 备注 */
    @Size(max = 500, message = "备注不得超过 500 字符")
    private String remark;
}
