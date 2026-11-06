package com.bluewhale.lms.exam.domain.model;

import lombok.Data;

/**
 * 题库查询条件。
 *
 * @author BlueWhale
 */
@Data
public class QuestionQuery {

    /** 关键字（题干 / 编码） */
    private String keyword;

    /** 分类 */
    private String category;

    /** 题型 */
    private String type;

    /** 难度 */
    private String difficulty;

    /** 状态 */
    private String status;

    /** 页码 */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;
}
