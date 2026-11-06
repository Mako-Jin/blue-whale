package com.bluewhale.lms.cert.domain.model;

import lombok.Data;

/**
 * 证书模板查询条件。
 *
 * @author BlueWhale
 */
@Data
public class CertTemplateQuery {

    /** 关键字（编码 / 名称模糊） */
    private String keyword;

    /** 类别 */
    private String category;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 页码（从 1 起） */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;
}
