package com.bluewhale.lms.exam.domain.model;

import lombok.Data;

/**
 * 考试查询条件。
 *
 * @author BlueWhale
 */
@Data
public class ExamQuery {

    /** 关键字（名称 / 编码） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 页码 */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;
}
