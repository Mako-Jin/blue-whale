package com.bluewhale.doc.application.service;

import com.bluewhale.doc.interfaces.dto.DocCommentCreateRequest;
import com.bluewhale.doc.interfaces.vo.DocCommentUnreadVO;
import com.bluewhale.doc.interfaces.vo.DocCommentVO;

import java.util.List;

/**
 * 页面评论应用服务。
 *
 * @author BlueWhale
 */
public interface DocCommentService {

    /**
     * 查询页面评论（主评论含回复）。
     *
     * @param pageId   页面ID
     * @param resolved 解决状态过滤；null 表示全部
     * @param userId   当前用户ID
     * @return 评论列表
     */
    List<DocCommentVO> list(Long pageId, Boolean resolved, Long userId);

    /**
     * 新增评论或回复。
     *
     * @param pageId  页面ID
     * @param request 评论内容
     * @param userId  当前用户ID
     * @return 新建评论
     */
    DocCommentVO create(Long pageId, DocCommentCreateRequest request, Long userId);

    /**
     * 解决或重新打开评论。
     *
     * @param commentId 评论ID
     * @param resolved  目标解决状态
     * @param userId    当前用户ID
     * @return 更新后的评论
     */
    DocCommentVO resolve(Long commentId, Boolean resolved, Long userId);

    /**
     * 删除评论（主评论级联删除回复）。
     *
     * @param commentId 评论ID
     * @param userId    当前用户ID
     */
    void delete(Long commentId, Long userId);

    /**
     * 查询页面评论未读数（不含本人评论）。
     *
     * @param pageId 页面ID
     * @param userId 当前用户ID
     * @return 未读信息
     */
    DocCommentUnreadVO unread(Long pageId, Long userId);

    /**
     * 将页面评论标记为已读。
     *
     * @param pageId 页面ID
     * @param userId 当前用户ID
     * @return 标记后的未读信息
     */
    DocCommentUnreadVO markRead(Long pageId, Long userId);
}
