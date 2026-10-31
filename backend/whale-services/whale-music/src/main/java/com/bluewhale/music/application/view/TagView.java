package com.bluewhale.music.application.view;

import com.bluewhale.music.domain.model.TagType;
import lombok.Data;

/**
 * 标签视图。
 *
 * @author BlueWhale
 */
@Data
public class TagView {

    /** 主键 */
    private Long id;

    /** 名称 */
    private String name;

    /** 类型 */
    private TagType tagType;

    /** 排序 */
    private Integer sortNo;
}
