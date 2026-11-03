package com.bluewhale.video.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.video.application.command.CollectionQueryCommand;
import com.bluewhale.video.application.command.MovieCollectionItemCommand;
import com.bluewhale.video.application.command.MovieCollectionSaveCommand;
import com.bluewhale.video.application.view.MovieCollectionDetailView;
import com.bluewhale.video.application.view.MovieCollectionView;

/**
 * 电影片单应用服务：运营维护片单与加片 / 移除，用户侧列表与详情。
 *
 * <p>同一片单同一电影唯一（应用层幂等判断 + 数据库唯一键兜底），
 * 单片单最多 200 部；片单下架不影响电影自身状态。</p>
 *
 * @author BlueWhale
 */
public interface MovieCollectionAppService {

    /**
     * 运营片单分页（含草稿与已下架）。
     *
     * @param command 查询命令
     * @return 片单分页
     */
    PageResult<MovieCollectionView> adminPage(CollectionQueryCommand command);

    /**
     * 运营读取单个片单（含全部条目）。
     *
     * @param id 片单 ID
     * @return 片单详情
     */
    MovieCollectionDetailView adminDetail(Long id);

    /**
     * 新增片单草稿。
     *
     * @param command 保存命令
     * @return 片单视图
     */
    MovieCollectionView create(MovieCollectionSaveCommand command);

    /**
     * 编辑片单。
     *
     * @param id 片单 ID
     * @param command 保存命令
     * @return 片单视图
     */
    MovieCollectionView update(Long id, MovieCollectionSaveCommand command);

    /**
     * 删除片单（逻辑删除并物理删除条目）。
     *
     * @param id 片单 ID
     */
    void delete(Long id);

    /**
     * 上架片单。
     *
     * @param id 片单 ID
     * @return 片单视图
     */
    MovieCollectionView publish(Long id);

    /**
     * 下架片单。
     *
     * @param id 片单 ID
     * @return 片单视图
     */
    MovieCollectionView offline(Long id);

    /**
     * 加片（同一电影重复加片幂等）。
     *
     * @param id 片单 ID
     * @param command 加片命令
     * @return 片单详情
     */
    MovieCollectionDetailView addItem(Long id, MovieCollectionItemCommand command);

    /**
     * 移除片单内电影（不存在时幂等）。
     *
     * @param id 片单 ID
     * @param contentId 电影内容 ID
     * @return 片单详情
     */
    MovieCollectionDetailView removeItem(Long id, Long contentId);

    /**
     * 用户侧片单分页（仅已上架）。
     *
     * @param command 查询命令
     * @return 片单分页
     */
    PageResult<MovieCollectionView> publicPage(CollectionQueryCommand command);

    /**
     * 用户侧片单详情（仅已上架，包含片单内可见电影）。
     *
     * @param id 片单 ID
     * @return 片单详情
     */
    MovieCollectionDetailView publicDetail(Long id);

    /**
     * 读取封面文件 ID（静态代理内部使用）。
     *
     * @param id 片单 ID
     * @return 封面文件 ID；无封面返回 {@code null}
     */
    Long coverFileId(Long id);
}
