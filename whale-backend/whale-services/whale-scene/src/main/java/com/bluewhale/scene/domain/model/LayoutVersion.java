package com.bluewhale.scene.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 场景布局发布快照。
 *
 * <p>每次发布生成一条不可变快照，{@code checksum} 用于检测布局内容被篡改或
 * 传输损坏；快照只保存布局与入口元数据，不包含用户数据。</p>
 *
 * @author BlueWhale
 */
@Data
public class LayoutVersion {

    /** 主键 */
    private Long id;

    /** 所属场景 ID */
    private Long sceneId;

    /** 版本号（场景内递增，从 1 开始） */
    private Integer version;

    /** 布局 JSON（节点树 + 入口） */
    private String layoutJson;

    /** 布局内容 SHA-256 校验和 */
    private String checksum;

    /** 创建时间 */
    private LocalDateTime createTime;
}
