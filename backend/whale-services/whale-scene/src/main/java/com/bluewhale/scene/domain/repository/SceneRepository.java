package com.bluewhale.scene.domain.repository;

import com.bluewhale.scene.domain.model.Scene;
import com.bluewhale.scene.domain.model.SceneQuery;

import java.util.List;
import java.util.Optional;

/**
 * 场景聚合仓储端口。
 *
 * @author BlueWhale
 */
public interface SceneRepository {

    /**
     * 分页查询场景。
     *
     * @param query 查询条件
     * @return 当前页场景
     */
    List<Scene> page(SceneQuery query);

    /**
     * 统计场景总数。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(SceneQuery query);

    /**
     * 按主键查询场景（租户隔离由持久化插件保证）。
     *
     * @param id 场景主键
     * @return 场景
     */
    Optional<Scene> findById(Long id);

    /**
     * 按场景标识查询场景。
     *
     * @param sceneKey 场景标识
     * @return 场景
     */
    Optional<Scene> findByKey(String sceneKey);

    /**
     * 查询全部已发布场景。
     *
     * @return 已发布场景列表
     */
    List<Scene> listPublished();

    /**
     * 新增场景并回填主键与审计时间。
     *
     * @param scene 场景
     */
    void insert(Scene scene);

    /**
     * 更新场景可变字段。
     *
     * @param scene 场景
     */
    void update(Scene scene);
}
