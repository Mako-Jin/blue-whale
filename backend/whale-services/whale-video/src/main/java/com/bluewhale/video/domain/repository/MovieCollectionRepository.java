package com.bluewhale.video.domain.repository;

import com.bluewhale.video.domain.model.CollectionQuery;
import com.bluewhale.video.domain.model.MovieCollection;

import java.util.List;
import java.util.Optional;

/**
 * 电影片单仓储端口（含片单条目关系）。
 *
 * @author BlueWhale
 */
public interface MovieCollectionRepository {

    /**
     * 按主键查询片单。
     *
     * @param id 片单 ID
     * @return 片单；不存在返回空
     */
    Optional<MovieCollection> findById(Long id);

    /**
     * 分页查询片单（按运营排序升序、同序按 ID 倒序）。
     *
     * @param query 查询条件
     * @return 当前页片单
     */
    List<MovieCollection> page(CollectionQuery query);

    /**
     * 统计符合条件的片单总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(CollectionQuery query);

    /**
     * 新增片单。
     *
     * @param collection 片单
     */
    void insert(MovieCollection collection);

    /**
     * 更新片单。
     *
     * @param collection 片单
     */
    void update(MovieCollection collection);

    /**
     * 逻辑删除片单并物理删除其条目。
     *
     * @param id 片单 ID
     */
    void delete(Long id);

    /**
     * 查询片单内电影内容 ID（按片单内排序升序）。
     *
     * @param collectionId 片单 ID
     * @return 内容 ID 列表；无条目返回空列表
     */
    List<Long> listItemContentIds(Long collectionId);

    /**
     * 统计片单条目数。
     *
     * @param collectionId 片单 ID
     * @return 条目数
     */
    int countItems(Long collectionId);

    /**
     * 判断片单内是否已存在指定电影。
     *
     * @param collectionId 片单 ID
     * @param contentId 电影内容 ID
     * @return 已存在返回 {@code true}
     */
    boolean existsItem(Long collectionId, Long contentId);

    /**
     * 新增片单条目（同一电影重复加片由应用层拦截；DB 唯一键兜底）。
     *
     * @param collectionId 片单 ID
     * @param contentId 电影内容 ID
     * @param sortNo 片单内排序
     */
    void addItem(Long collectionId, Long contentId, int sortNo);

    /**
     * 移除片单条目。
     *
     * @param collectionId 片单 ID
     * @param contentId 电影内容 ID
     */
    void removeItem(Long collectionId, Long contentId);

    /**
     * 查询收录指定电影的已上架片单（按运营排序升序、同序按 ID 倒序）。
     *
     * @param contentId 电影内容 ID
     * @param limit 最多返回条数
     * @return 已上架片单列表；未收录返回空列表
     */
    List<MovieCollection> listPublishedByContentId(Long contentId, int limit);
}
