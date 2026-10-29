package com.bluewhale.doc.domain.model;

/**
 * 文档空间可见性。
 *
 * @author BlueWhale
 */
public enum DocVisibility {

    /** 公开：租户内成员可读取已发布页面，公开搜索可见 */
    PUBLIC,

    /** 成员可见：仅空间成员可读写 */
    MEMBER
}
