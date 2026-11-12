package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 鲸阶描述（F-07-03）。
 *
 * @author BlueWhale
 */
@Data
public class LevelVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 等级序号（从 1 起） */
    private Integer level;

    /** 等级名称（如「听潮」） */
    private String name;

    /** 本级起始经验 */
    private Long minExp;

    /** 下一级起始经验（已满级为 null） */
    private Long nextExp;
}
