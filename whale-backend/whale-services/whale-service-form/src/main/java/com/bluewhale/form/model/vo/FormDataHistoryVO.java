package com.bluewhale.form.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表单数据变更历史视图对象（字段名为冻结的 JSON 契约，不得改名）。
 *
 * @author BlueWhale
 */
@Data
public class FormDataHistoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 历史记录主键ID */
    private Long id;

    /** 表单数据ID */
    private Long dataId;

    /** 表单编码 */
    private String definitionCode;

    /** 变更类型：CREATE / UPDATE / DELETE */
    private String changeType;

    /** 变更前数据JSON（CREATE 为空） */
    private String beforeJson;

    /** 变更后数据JSON（DELETE 为空） */
    private String afterJson;

    /** 本次变更的字段编码（逗号分隔） */
    private String changedKeys;

    /** 操作人账号 */
    private String operator;

    /** 变更时间 */
    private LocalDateTime changeTime;
}
