package com.bluewhale.video.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.video.application.command.ContentQueryCommand;
import com.bluewhale.video.application.command.ContentSaveCommand;
import com.bluewhale.video.application.view.ContentDetailView;
import com.bluewhale.video.application.view.ContentView;
import com.bluewhale.video.application.view.OwnerContentView;
import com.bluewhale.video.application.view.PublicContentView;

/**
 * 内容应用服务：草稿创建 / 编辑 / 删除、发布与下架、用户侧浏览与运营分页。
 *
 * @author BlueWhale
 */
public interface ContentAppService {

    /**
     * 创建内容草稿（用户侧需先签署当前声明）。
     *
     * @param command 保存命令
     * @param userId 当前用户 ID
     * @return 运营视图
     */
    ContentView create(ContentSaveCommand command, Long userId);

    /**
     * 更新本人内容（仅上传者可编辑）。
     *
     * @param id 内容 ID
     * @param command 保存命令
     * @param userId 当前用户 ID
     * @return 运营视图
     */
    ContentView update(Long id, ContentSaveCommand command, Long userId);

    /**
     * 逻辑删除本人内容。
     *
     * @param id 内容 ID
     * @param userId 当前用户 ID
     */
    void delete(Long id, Long userId);

    /**
     * 上传者发布本人内容（草稿 / 已下线可发布）。
     *
     * @param id 内容 ID
     * @param userId 当前用户 ID
     * @return 运营视图
     */
    ContentView publishByOwner(Long id, Long userId);

    /**
     * 上传者下架本人内容。
     *
     * @param id 内容 ID
     * @param userId 当前用户 ID
     * @return 运营视图
     */
    ContentView offlineByOwner(Long id, Long userId);

    /**
     * 我的作品分页（按内容类型，返回发布与版权状态）。
     *
     * @param command 查询命令
     * @param contentType 内容类型
     * @param userId 当前用户 ID
     * @return 上传者侧分页结果
     */
    PageResult<OwnerContentView> myContent(ContentQueryCommand command, String contentType, Long userId);

    /**
     * 读取本人作品编辑视图（含内部文件引用，仅上传者可见）。
     *
     * @param id 内容 ID
     * @param userId 当前用户 ID
     * @return 运营视图（供编辑回填）
     */
    ContentView ownerView(Long id, Long userId);

    /**
     * 用户侧内容详情（不可见内容按不存在处理）。
     *
     * @param id 内容 ID
     * @param userId 当前用户 ID
     * @return 详情视图
     */
    ContentDetailView detail(Long id, Long userId);

    /**
     * 用户侧内容分页（仅已发布且可见内容）。
     *
     * @param command 查询命令
     * @param userId 当前用户 ID
     * @return 用户侧分页结果
     */
    PageResult<PublicContentView> publicPage(ContentQueryCommand command, Long userId);

    /**
     * 运营内容分页（含草稿与已下线）。
     *
     * @param command 查询命令
     * @return 运营视图分页结果
     */
    PageResult<ContentView> adminPage(ContentQueryCommand command);

    /**
     * 运营发布内容。
     *
     * @param id 内容 ID
     * @return 运营视图
     */
    ContentView adminPublish(Long id);

    /**
     * 运营下架内容。
     *
     * @param id 内容 ID
     * @return 运营视图
     */
    ContentView adminOffline(Long id);

    /**
     * 运营版权下架内容（强制下线并阻断播放）。
     *
     * @param id 内容 ID
     * @param reason 下架原因
     * @return 运营视图
     */
    ContentView adminTakedown(Long id, String reason);

    /**
     * 读取内容封面静态代理视图所需的文件 ID（仅接口层内部使用）。
     *
     * @param contentId 内容 ID
     * @param userId 当前用户 ID
     * @return 封面文件 ID；无封面返回 {@code null}
     */
    Long coverFileId(Long contentId, Long userId);
}
