package com.bluewhale.scene.domain.model;

import lombok.Data;

/**
 * 场景分页查询条件（领域查询对象，不依赖分页框架）。
 *
 * @author BlueWhale
 */
@Data
public class SceneQuery {

    /** 关键字：匹配场景标识 / 名称 */
    private String keyword;

    /** 状态过滤，为空表示全部 */
    private String status;

    /** 当前页（从 1 开始） */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;
}
