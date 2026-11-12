package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 举报工单视图。
 *
 * @author BlueWhale
 */
@Data
public class ReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单ID */
    private Long id;

    /** 对象类型 */
    private String targetType;

    /** 对象ID */
    private Long targetId;

    /** 对象标题/摘要（帖子取标题，楼层取所属帖子标题） */
    private String targetTitle;

    /** 举报人 */
    private Long reporterId;

    /** 举报类型 */
    private String reportType;

    /** 举报说明 */
    private String description;

    /** 状态 */
    private String status;

    /** 处理人 */
    private Long handlerId;

    /** 处理意见 */
    private String handleRemark;

    /** 处理时间 */
    private LocalDateTime handleTime;

    /** 提交时间 */
    private LocalDateTime createTime;
}
