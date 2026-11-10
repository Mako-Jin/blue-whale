package com.bluewhale.lms.cert.domain.model;

import lombok.Data;

/**
 * 证书查询条件。
 *
 * <p>{@link #status} 允许取 {@link CertStatus#EXPIRED}——它不是落库值，而是一个筛选意图：
 * {@code EXPIRED} 表示「落库有效但已过有效期」，{@code ACTIVE} 表示「落库有效且未过期」。
 * 这样前端一个下拉就能覆盖四种展示态，不需要额外拼时间条件。</p>
 *
 * @author BlueWhale
 */
@Data
public class CertificateQuery {

    /** 关键字（编号 / 持证人 / 证书名称模糊） */
    private String keyword;

    /** 证书模板 ID */
    private Long templateId;

    /** 持证人用户 ID */
    private Long userId;

    /** 来源类型 */
    private String sourceType;

    /** 展示状态：ACTIVE / EXPIRED / REVOKED / RENEWED */
    private String status;

    /** 页码（从 1 起） */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;
}
