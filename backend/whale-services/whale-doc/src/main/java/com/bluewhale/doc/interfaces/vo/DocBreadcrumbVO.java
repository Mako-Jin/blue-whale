package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 页面面包屑节点。
 *
 * @author BlueWhale
 */
@Data
public class DocBreadcrumbVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long id;

    /** 页面标题 */
    private String title;
}
