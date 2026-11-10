package com.bluewhale.workflow.support;

import lombok.Data;

/**
 * 流程模型分组 / 泳道（规范化 JSON 的 {@code groups[]} 元素）。
 *
 * <p>仅承载语义元数据：容器矩形由成员节点的画布坐标派生，不持久化容器坐标。
 * 节点通过 {@link ProcessModelNode#getGroupKey()} 归属到分组。</p>
 *
 * @author BlueWhale
 */
@Data
public class ProcessModelGroup {

    /** 分组键（流程内唯一，字符集同节点 key：字母/数字/下划线/连字符，1~64 字符） */
    private String key;

    /** 分组名称（可空） */
    private String name;
}
