package com.bluewhale.twin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标注/热点视图对象。
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkerVO {

    /** 标注ID（字符串化） */
    private String id;

    /** 场景ID（字符串化） */
    private String sceneId;

    /** 挂靠实体ID（字符串化，可空） */
    private String entityId;

    /** 实体编码（冗余展示，可空） */
    private String entityCode;

    /** 实体名称（冗余展示，可空） */
    private String entityName;

    /** 类型：label/hotspot/alarm */
    private String markerType;

    /** 标注内容（可空） */
    private String content;

    /** 三维坐标 JSON（可空） */
    private String positionJson;

    /** 跳转地址（可空） */
    private String linkUrl;

    /** 是否启用：0/1 */
    private Integer enabled;

    /** 创建时间（ISO-8601） */
    private String createTime;
}
