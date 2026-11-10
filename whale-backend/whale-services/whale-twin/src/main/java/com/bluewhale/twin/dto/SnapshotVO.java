package com.bluewhale.twin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发布快照视图对象。
 *
 * <p>{@code snapshotJson} 仅在详情接口返回（列表不携带整包 JSON，
 * 避免响应膨胀）。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotVO {

    /** 快照ID（字符串化） */
    private String id;

    /** 场景ID（字符串化） */
    private String sceneId;

    /** 快照版本号 */
    private Integer version;

    /** 发布说明（可空） */
    private String remark;

    /** 发布人（字符串化，可空） */
    private String publishedBy;

    /** 发布时间（ISO-8601） */
    private String publishTime;

    /** 快照整包 JSON（仅详情返回，可空） */
    private String snapshotJson;
}
