package com.bluewhale.doc.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.doc.infrastructure.persistence.query.DocTrashRootRow;
import com.bluewhale.doc.interfaces.vo.DocRecycleItemVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档回收站应用服务（M2）。
 *
 * <p>删除页面时整棵子树进入回收站；恢复与彻底清除均以子树根节点为操作单位。
 * 数据权限统一要求空间 ADMIN 及以上。</p>
 *
 * @author BlueWhale
 */
public interface DocRecycleService {

    /**
     * 将页面及其整棵子树移入回收站。
     *
     * @param pageId 页面ID
     * @param userId 操作人ID
     */
    void trash(Long pageId, Long userId);

    /**
     * 分页查询空间回收站根节点。
     *
     * @param spaceId 空间ID
     * @param current 当前页（从 1 开始）
     * @param size    每页条数
     * @param userId  当前用户ID
     * @return 回收站分页
     */
    PageResult<DocRecycleItemVO> page(Long spaceId, long current, long size, Long userId);

    /**
     * 恢复回收站子树。
     *
     * @param pageId 回收站根页面ID
     * @param userId 操作人ID
     */
    void restore(Long pageId, Long userId);

    /**
     * 彻底清除回收站子树（物理删除页面及版本/评论/附件/收藏）。
     *
     * @param pageId 回收站根页面ID
     * @param userId 操作人ID
     */
    void purge(Long pageId, Long userId);

    /**
     * 清空空间回收站。
     *
     * @param spaceId 空间ID
     * @param userId  操作人ID
     */
    void purgeSpace(Long spaceId, Long userId);

    /**
     * 查询已到期回收站根节点（跨租户），供定时清理任务使用。
     *
     * @param cutoff 到期时间阈值
     * @param limit  单轮最大数量
     * @return 到期根节点行
     */
    List<DocTrashRootRow> expiredRoots(LocalDateTime cutoff, int limit);

    /**
     * 按租户彻底清除一个到期回收站子树。
     *
     * @param tenantId 租户ID
     * @param rootId   回收站根页面ID
     */
    void purgeExpired(Long tenantId, Long rootId);
}
