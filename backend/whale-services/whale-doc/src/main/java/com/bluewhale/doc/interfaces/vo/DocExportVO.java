package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 文档导出内容（M3）。
 *
 * @author BlueWhale
 */
@Data
public class DocExportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 导出文件名 */
    private String fileName;

    /** MIME 类型 */
    private String contentType;

    /** 文件内容 */
    private byte[] content;
}
