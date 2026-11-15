package com.bluewhale.form.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 表单数据批量导入结果视图对象（字段名为冻结的 JSON 契约，不得改名）。
 *
 * @author BlueWhale
 */
@Data
public class FormDataImportResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据行总数（不含表头与空行） */
    private Integer totalCount;

    /** 成功导入行数 */
    private Integer successCount;

    /** 失败行数 */
    private Integer failCount;

    /** 失败行明细（成功时为空列表） */
    private List<FormDataImportFailRowVO> failRows;
}
