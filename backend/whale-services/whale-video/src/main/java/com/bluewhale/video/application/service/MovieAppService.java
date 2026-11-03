package com.bluewhale.video.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.video.application.command.MovieQueryCommand;
import com.bluewhale.video.application.command.MovieSaveCommand;
import com.bluewhale.video.application.view.MovieAdminView;
import com.bluewhale.video.application.view.MovieDetailView;
import com.bluewhale.video.application.view.MovieView;

/**
 * 电影应用服务：运营入库 / 授权 / 上下架，用户侧列表与详情。
 *
 * <p>电影内容与元数据 1:1 扩展：{@code vd_content} 保存文件与播放状态，
 * {@code vd_movie} 保存导演 / 演员 / 地区 / 语言 / 上映日期 / 评分与授权信息。
 * 授权到期后内容不可播放，并由定时任务自动下线。</p>
 *
 * @author BlueWhale
 */
public interface MovieAppService {

    /**
     * 运营电影分页（含草稿与已下线）。
     *
     * @param command 查询命令
     * @return 电影运营视图分页
     */
    PageResult<MovieAdminView> adminPage(MovieQueryCommand command);

    /**
     * 运营读取单部电影（供编辑回填）。
     *
     * @param id 内容 ID
     * @return 电影运营视图
     */
    MovieAdminView adminDetail(Long id);

    /**
     * 电影入库（创建内容草稿与元数据）。
     *
     * @param command 保存命令
     * @return 电影运营视图
     */
    MovieAdminView create(MovieSaveCommand command);

    /**
     * 编辑电影元数据与授权。
     *
     * @param id 内容 ID
     * @param command 保存命令
     * @return 电影运营视图
     */
    MovieAdminView update(Long id, MovieSaveCommand command);

    /**
     * 删除电影（内容逻辑删除 + 元数据物理删除）。
     *
     * @param id 内容 ID
     */
    void delete(Long id);

    /**
     * 发布电影（授权必须仍在有效期）。
     *
     * @param id 内容 ID
     * @return 电影运营视图
     */
    MovieAdminView publish(Long id);

    /**
     * 下架电影。
     *
     * @param id 内容 ID
     * @return 电影运营视图
     */
    MovieAdminView offline(Long id);

    /**
     * 版权下架电影（强制下线并阻断播放）。
     *
     * @param id 内容 ID
     * @param reason 下架原因
     * @return 电影运营视图
     */
    MovieAdminView takedown(Long id, String reason);

    /**
     * 用户侧电影分页（仅已发布且可见）。
     *
     * @param command 查询命令
     * @param userId 当前用户 ID，可为 {@code null}
     * @return 电影分页
     */
    PageResult<MovieView> publicPage(MovieQueryCommand command, Long userId);

    /**
     * 用户侧电影详情。
     *
     * @param id 内容 ID
     * @param userId 当前用户 ID，可为 {@code null}
     * @return 电影详情
     */
    MovieDetailView detail(Long id, Long userId);

    /**
     * 读取海报文件 ID（静态代理内部使用）。
     *
     * @param contentId 内容 ID
     * @param userId 当前用户 ID，可为 {@code null}
     * @return 海报文件 ID；无海报返回 {@code null}
     */
    Long posterFileId(Long contentId, Long userId);

    /**
     * 读取背景图文件 ID（静态代理内部使用）。
     *
     * @param contentId 内容 ID
     * @param userId 当前用户 ID，可为 {@code null}
     * @return 背景图文件 ID；无背景图返回 {@code null}
     */
    Long backdropFileId(Long contentId, Long userId);
}
