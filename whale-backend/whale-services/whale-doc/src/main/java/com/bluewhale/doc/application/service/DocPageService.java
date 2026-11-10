package com.bluewhale.doc.application.service;

import com.bluewhale.doc.interfaces.dto.DocPageCreateRequest;
import com.bluewhale.doc.interfaces.dto.DocPageMoveRequest;
import com.bluewhale.doc.interfaces.dto.DocPageSaveRequest;
import com.bluewhale.doc.interfaces.vo.DocPageSaveVO;
import com.bluewhale.doc.interfaces.vo.DocPageTreeVO;
import com.bluewhale.doc.interfaces.vo.DocPageVO;

import java.util.List;

/**
 * 文档页面与页面树应用服务。
 *
 * @author BlueWhale
 */
public interface DocPageService {

    /**
     * 查询空间页面树；非成员只能看到公开空间的已发布页面。
     *
     * @param spaceId 空间ID
     * @param userId  当前用户ID
     * @return 页面树
     */
    List<DocPageTreeVO> tree(Long spaceId, Long userId);

    /**
     * 查询页面详情（含正文、权限标记与面包屑）。
     *
     * @param pageId 页面ID
     * @param userId 当前用户ID
     * @return 页面详情
     */
    DocPageVO detail(Long pageId, Long userId);

    /**
     * 按空间与路径定位页面。
     *
     * @param spaceId 空间ID
     * @param slug    页面路径
     * @param userId  当前用户ID
     * @return 页面详情
     */
    DocPageVO bySlug(Long spaceId, String slug, Long userId);

    /**
     * 新建页面或目录。
     *
     * @param request 新建请求
     * @param userId  当前用户ID
     * @return 新建页面详情
     */
    DocPageVO create(DocPageCreateRequest request, Long userId);

    /**
     * 保存页面标题与正文（乐观锁）。
     *
     * @param pageId  页面ID
     * @param request 保存请求
     * @param userId  当前用户ID
     * @return 保存结果
     */
    DocPageSaveVO save(Long pageId, DocPageSaveRequest request, Long userId);

    /**
     * 仅更新页面标题（乐观锁）。
     *
     * @param pageId  页面ID
     * @param request 保存请求（需携带 baseRevision 与 title）
     * @param userId  当前用户ID
     * @return 保存结果
     */
    DocPageSaveVO updateTitle(Long pageId, DocPageSaveRequest request, Long userId);

    /**
     * 发布页面并写入发布快照。
     *
     * @param pageId 页面ID
     * @param userId 当前用户ID
     * @return 保存结果
     */
    DocPageSaveVO publish(Long pageId, Long userId);

    /**
     * 归档页面。
     *
     * @param pageId 页面ID
     * @param userId 当前用户ID
     * @return 保存结果
     */
    DocPageSaveVO archive(Long pageId, Long userId);

    /**
     * 从归档恢复为草稿。
     *
     * @param pageId 页面ID
     * @param userId 当前用户ID
     * @return 保存结果
     */
    DocPageSaveVO restore(Long pageId, Long userId);

    /**
     * 移动页面到新的父级并调整排序。
     *
     * @param pageId  页面ID
     * @param request 移动请求
     * @param userId  当前用户ID
     */
    void move(Long pageId, DocPageMoveRequest request, Long userId);

    /**
     * 删除页面（仅无子页面，级联逻辑删除附件、评论与收藏）。
     *
     * @param pageId 页面ID
     * @param userId 当前用户ID
     */
    void delete(Long pageId, Long userId);
}
