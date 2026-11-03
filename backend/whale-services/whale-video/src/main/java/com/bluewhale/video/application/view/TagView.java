package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.Serializable;

/**
 * 标签视图。
 *
 * @author BlueWhale
 */
@Data
public class TagView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 标签 ID */
    private Long id;

    /** 标签名称 */
    private String name;

    /** 标签类型 */
    private String tagType;

    /** 排序 */
    private Integer sortNo;
}
