package com.bluewhale.video.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.video.application.command.SeriesQueryCommand;
import com.bluewhale.video.application.command.SeriesSaveCommand;
import com.bluewhale.video.application.view.SeriesDetailView;
import com.bluewhale.video.application.view.SeriesView;

/**
 * 系列应用服务：运营维护系列，用户侧查看系列与集数。
 *
 * @author BlueWhale
 */
public interface SeriesAppService {

    /**
     * 运营系列分页（含草稿与已下架）。
     *
     * @param command 查询命令
     * @return 系列分页结果
     */
    PageResult<SeriesView> adminPage(SeriesQueryCommand command);

    /**
     * 用户侧系列分页（仅已上架系列，供视频列表与上传时选择系列）。
     *
     * @param command 查询命令
     * @return 系列分页结果
     */
    PageResult<SeriesView> publishedPage(SeriesQueryCommand command);

    /**
     * 新增系列草稿。
     *
     * @param command 保存命令
     * @param userId 创建人用户 ID（运营）
     * @return 系列视图
     */
    SeriesView create(SeriesSaveCommand command, Long userId);

    /**
     * 更新系列。
     *
     * @param id 系列 ID
     * @param command 保存命令
     * @return 系列视图
     */
    SeriesView update(Long id, SeriesSaveCommand command);

    /**
     * 删除系列（存在已发布集数时拒绝删除）。
     *
     * @param id 系列 ID
     */
    void delete(Long id);

    /**
     * 上架系列。
     *
     * @param id 系列 ID
     * @return 系列视图
     */
    SeriesView publish(Long id);

    /**
     * 下架系列。
     *
     * @param id 系列 ID
     * @return 系列视图
     */
    SeriesView offline(Long id);

    /**
     * 用户侧系列详情（未上架系列按不存在处理）。
     *
     * @param id 系列 ID
     * @return 系列详情视图
     */
    SeriesDetailView detail(Long id);

    /**
     * 读取系列封面文件 ID（仅接口层静态代理内部使用）。
     *
     * @param id 系列 ID
     * @return 封面文件 ID；未上架或无封面返回 {@code null}
     */
    Long coverFileId(Long id);
}
