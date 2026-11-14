package com.bluewhale.form.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 表单数据导出产物（响应体内容 + 文件名 + Content-Type），供控制器直接写出下载响应。
 *
 * @author BlueWhale
 */
@Data
@AllArgsConstructor
public class FormDataExportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文件内容 */
    private byte[] content;

    /** 下载文件名（含扩展名） */
    private String filename;

    /** 响应 Content-Type */
    private String contentType;

    /** 是否因超出单次导出上限而被截断 */
    private boolean truncated;
}
