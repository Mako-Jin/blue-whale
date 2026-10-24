package com.bluewhale.archive.remote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 表单数据（跨服务内网响应体）。
 *
 * @author BlueWhale
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteFormData {

    /** 表单数据ID */
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 表单定义编码 */
    private String definitionCode;

    /** 表单定义版本 */
    private Integer definitionVersion;

    /** 业务键 */
    private String bizKey;

    /** 表单数据 JSON */
    private String dataJson;

    /** 结构化字段值 */
    private Map<String, Object> fields;

    /** 提交人账号 */
    private String submitter;

    /** 状态：1 有效 / 0 作废 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
