package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 文档导入结果（M3）。
 *
 * @author BlueWhale
 */
@Data
public class DocImportResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long pageId;

    /** 导入后的 revision */
    private Long revision;

    /** 页面状态 */
    private String status;

    /** 导入源格式：MD / HTML */
    private String format;
}
