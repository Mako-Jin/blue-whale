package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 申诉工单视图。
 *
 * @author BlueWhale
 */
@Data
public class AppealVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单ID */
    private Long id;

    /** 申诉人 */
    private Long userId;

    /** 对象类型：THREAD / POST / BAN */
    private String targetType;

    /** 对象ID */
    private Long targetId;

    /** 申诉理由 */
    private String reason;

    /** 状态：PENDING / APPROVED / REJECTED */
    private String status;

    /** 复核意见 */
    private String handleRemark;

    /** 复核时间 */
    private LocalDateTime handleTime;

    /** 提交时间 */
    private LocalDateTime createTime;

    /** 申诉材料（可能为空列表，不返回 {@code null}） */
    private List<AppealMaterialVO> materials;
}
