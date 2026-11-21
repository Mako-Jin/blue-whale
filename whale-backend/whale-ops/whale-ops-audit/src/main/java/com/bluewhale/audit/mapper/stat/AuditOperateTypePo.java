package com.bluewhale.audit.mapper.stat;

import lombok.Data;

/**
 * 按操作类型聚合的审计条数（起居殿概览 T5.9）。
 *
 * @author BlueWhale
 */
@Data
public class AuditOperateTypePo {

    /** 操作类型：LOGIN/CREATE/UPDATE/DELETE/QUERY/GRANT/EXPORT/IMPORT/OTHER */
    private String operateType;

    /** 该类型的审计条数 */
    private Long audits;
}
