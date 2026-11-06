package com.bluewhale.lms.exam.domain.model;

import lombok.Data;

/**
 * 作答记录查询条件。
 *
 * @author BlueWhale
 */
@Data
public class AttemptQuery {

    /** 考试 ID */
    private Long examId;

    /** 学员用户 ID */
    private Long userId;

    /** 状态（SUBMITTED 用于待判队列） */
    private String status;

    /** 页码 */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;
}
