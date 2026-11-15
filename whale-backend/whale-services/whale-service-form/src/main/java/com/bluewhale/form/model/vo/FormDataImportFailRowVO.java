package com.bluewhale.form.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 表单数据批量导入失败行视图对象（字段名为冻结的 JSON 契约，不得改名）。
 *
 * @author BlueWhale
 */
@Data
public class FormDataImportFailRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文件行号（从 2 开始，第 1 行为表头） */
    private Integer rowNo;

    /** 该行错误原因（可多项） */
    private List<String> errors;
}
