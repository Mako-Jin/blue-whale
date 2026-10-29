package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 文档标签视图。
 *
 * @author BlueWhale
 */
@Data
public class DocTagVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 标签ID */
    private Long id;

    /** 标签名称 */
    private String name;

    /** 标签颜色 */
    private String color;

    /** 可读页面数量（空间标签列表返回） */
    private Long pageCount;
}
