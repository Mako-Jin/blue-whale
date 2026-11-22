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
    @NotBlank(message = "{validation.lms.cert-template.template-code-required}")
    @Size(max = 64, message = "{validation.lms.cert-template.template-code-size}")
    private String templateCode;

    /** 证书名称 */
    @NotBlank(message = "{validation.lms.cert-template.name-required}")
    @Size(max = 128, message = "{validation.lms.cert-template.name-size}")
    private String name;

    /** 证书类别 */
    @Size(max = 32, message = "{validation.lms.cert-template.category-size}")
    private String category;

    /** 发证依据：MANUAL / COURSE / EXAM / TRAINING */
    private String issueBasis;

    /** 有效期月数（0 为长期有效） */
    @Min(value = 0, message = "{validation.lms.cert-template.valid-months-min}")
    @Max(value = 600, message = "{validation.lms.cert-template.valid-months-max}")
    private Integer validMonths;

    /** 编号前缀（大写字母数字，2–16 位） */
    private String certPrefix;

    /** 证书正文模板 */
    @Size(max = 4000, message = "{validation.lms.cert-template.content-size}")
    private String content;

    /** 备注 */
    @Size(max = 500, message = "{validation.lms.cert-template.remark-size}")
    private String remark;
}
