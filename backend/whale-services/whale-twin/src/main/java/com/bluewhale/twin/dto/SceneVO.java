package com.bluewhale.twin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 孪生场景视图对象。
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneVO {

    /** 场景ID（字符串化） */
    private String id;

    /** 场景编码 */
    private String code;

    /** 场景名称 */
    private String name;

    /** 场景描述 */
    private String description;

    /** 缩略图文件ID（字符串化，可空） */
    private String thumbnailFileId;

    /** 场景级配置 JSON 字符串（原样回显，可空） */
    private String sceneConfig;

    /** 当前编辑版本号 */
    private Integer version;

    /** 状态：draft/published/disabled */
    private String status;

    /** 实体数（管理列表展示用） */
    private Long entityCount;

    /** 创建人（字符串化，可空） */
    private String createdBy;

    /** 创建时间（ISO-8601） */
    private String createTime;

    /** 更新时间（ISO-8601） */
    private String updateTime;
}
