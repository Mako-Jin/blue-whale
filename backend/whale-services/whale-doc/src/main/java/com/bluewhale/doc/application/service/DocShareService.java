package com.bluewhale.doc.application.service;

import com.bluewhale.doc.interfaces.dto.DocPageShareCreateRequest;
import com.bluewhale.doc.interfaces.vo.DocPageShareVO;
import com.bluewhale.doc.interfaces.vo.DocPageVO;

import java.util.List;

/**
 * 页面登录内分享应用服务（M3）。
 *
 * @author BlueWhale
 */
public interface DocShareService {

    /**
     * 创建分享链接。
     *
     * @param pageId  页面ID
     * @param request 创建请求
     * @param userId  当前用户ID
     * @return 分享记录
     */
    DocPageShareVO create(Long pageId, DocPageShareCreateRequest request, Long userId);

    /**
     * 查询页面的分享记录。
     *
     * @param pageId 页面ID
     * @param userId 当前用户ID
     * @return 分享记录列表
     */
    List<DocPageShareVO> list(Long pageId, Long userId);

    /**
     * 撤销分享链接。
     *
     * @param pageId  页面ID
     * @param shareId 分享记录ID
     * @param userId  当前用户ID
     */
    void revoke(Long pageId, Long shareId, Long userId);

    /**
     * 按令牌读取分享页面（登录内只读）。
     *
     * @param token  分享令牌
     * @param userId 当前登录用户ID
     * @return 页面只读视图
     */
    DocPageVO resolve(String token, Long userId);
}
