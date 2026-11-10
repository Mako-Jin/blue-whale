package com.bluewhale.lms.course.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

/**
 * 课件（课程聚合内实体，挂在章节下）。
 *
 * @author BlueWhale
 */
@Data
public class Courseware {

    /** 主键 */
    private Long id;

    /** 所属课程 ID */
    private Long courseId;

    /** 所属章节 ID */
    private Long chapterId;

    /** 课件名称 */
    private String name;

    /** 类型：DOCUMENT / VIDEO / LINK */
    private WareType wareType;

    /** whale-file 文件引用 ID（LINK 类型为空） */
    private String fileRefId;

    /** 外部链接（仅 LINK 类型） */
    private String linkUrl;

    /** 排序号 */
    private Integer sortNo;

    /**
     * 校验课件完整性与类型约束。
     */
    public void validate() {
        if (name == null || name.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.COURSEWARE_STATE_CONFLICT, "课件名称不能为空");
        }
        if (wareType.requiresFileRef() && (fileRefId == null || fileRefId.isBlank())) {
            throw LmsErrorCode.exception(LmsErrorCode.COURSEWARE_STATE_CONFLICT,
                    wareType + " 类型课件必须携带 whale-file 文件引用");
        }
        if (wareType == WareType.LINK && (linkUrl == null || linkUrl.isBlank())) {
            throw LmsErrorCode.exception(LmsErrorCode.COURSEWARE_STATE_CONFLICT, "外链课件必须携带 URL");
        }
    }
}
