package com.bluewhale.form.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 表单数据视图对象（字段名为冻结的 JSON 契约，不得改名）。
 *
 * @author BlueWhale
 */
@Data
public class FormDataVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 表单定义 ID */
    private Long definitionId;

    /** 表单定义编码 */
    private String definitionCode;

    /** 表单定义版本 */
    private Integer definitionVersion;

    /** 业务键 */
    private String bizKey;

    /** 表单数据 JSON（过渡期双写字段） */
    private String dataJson;

    /** 结构化字段值（详情/提交返回；列表为 {@code null}） */
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
