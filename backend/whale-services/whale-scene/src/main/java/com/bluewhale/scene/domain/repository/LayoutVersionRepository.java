package com.bluewhale.scene.domain.repository;

import com.bluewhale.scene.domain.model.LayoutVersion;

import java.util.Optional;

/**
 * 布局版本快照仓储端口。
 *
 * @author BlueWhale
 */
public interface LayoutVersionRepository {

    /**
     * 查询场景最近一次发布的布局快照。
     *
     * @param sceneId 场景 ID
     * @return 布局快照
     */
    Optional<LayoutVersion> findLatest(Long sceneId);

    /**
     * 新增布局快照。
     *
     * @param layoutVersion 布局快照
     */
    void insert(LayoutVersion layoutVersion);
}
