package com.bluewhale.lms.cert.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 证书模板实体，对应表 {@code lr_cert_template}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_cert_template")
public class CertTemplateEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 模板编码（租户内唯一） */
    private String templateCode;

    /** 证书名称 */
    private String name;

    /** 证书类别 */
    private String category;

    /** 发证依据 */
    private String issueBasis;

    /** 有效期月数（0 为长期有效） */
    private Integer validMonths;

    /** 编号前缀 */
    private String certPrefix;

    /** 正文模板 */
    private String content;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
