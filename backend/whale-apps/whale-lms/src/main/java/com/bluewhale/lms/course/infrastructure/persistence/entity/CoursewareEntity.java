package com.bluewhale.lms.course.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课件实体，对应表 {@code lr_courseware}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_courseware")
public class CoursewareEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属课程 ID */
    private Long courseId;

    /** 所属章节 ID */
    private Long chapterId;

    /** 课件名称 */
    private String name;

    /** 类型：DOCUMENT / VIDEO / LINK */
    private String wareType;

    /** whale-file 文件引用 ID */
    private String fileRefId;

    /** 外部链接 */
    private String linkUrl;

    /** 排序号 */
    private Integer sortNo;
}
