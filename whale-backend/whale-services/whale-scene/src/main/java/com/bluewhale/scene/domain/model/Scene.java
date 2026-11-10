package com.bluewhale.scene.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 场景（城池）聚合根。
 *
 * <p>一个场景归属一个租户，{@code sceneKey} 在租户内唯一；发布动作只通过
 * {@link #publish(int, LocalDateTime)} 修改状态与布局版本，避免外部直接改字段
 * 绕过状态机。</p>
 *
 * @author BlueWhale
 */
@Data
public class Scene {

    /** 主键 */
    private Long id;

    /** 场景标识（租户内唯一，小写字母 / 数字 / 连字符） */
    private String sceneKey;

    /** 场景名称 */
    private String name;

    /** 场景说明 */
    private String description;

    /** 状态：DRAFT / PUBLISHED / OFFLINE */
    private String status;

    /** 当前已发布的布局版本号，0 表示从未发布 */
    private Integer layoutVersion;

    /** 最近发布时间 */
    private LocalDateTime publishedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 发布场景：状态置为已发布，布局版本 +1，记录发布时间。
     *
     * @param nextLayoutVersion 由布局快照生成的新版本号（必须大于当前版本）
     * @param publishedAt 发布时间
     */
    public void publish(int nextLayoutVersion, LocalDateTime publishedAt) {
        this.status = SceneStatus.PUBLISHED;
        this.layoutVersion = nextLayoutVersion;
        this.publishedAt = publishedAt;
    }

    /**
     * 下线场景：导航立即不可见，已发布布局版本保留用于回溯。
     */
    public void offline() {
        this.status = SceneStatus.OFFLINE;
    }
}
