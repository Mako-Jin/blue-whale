package com.bluewhale.doc.domain.model;

/**
 * 文档页面节点类型。
 *
 * @author BlueWhale
 */
public enum DocPageType {

    /** 正文页面 */
    PAGE,

    /** 目录节点：仅承载层级与排序，不保存正文 */
    FOLDER
}
