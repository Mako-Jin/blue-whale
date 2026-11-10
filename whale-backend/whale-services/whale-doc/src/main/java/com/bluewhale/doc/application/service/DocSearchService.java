package com.bluewhale.doc.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.doc.interfaces.dto.DocSearchQueryRequest;
import com.bluewhale.doc.interfaces.vo.DocSearchItemVO;

/**
 * 文档搜索应用服务。
 *
 * @author BlueWhale
 */
public interface DocSearchService {

    /**
     * 搜索可访问的已发布页面。
     *
     * @param request 搜索条件
     * @param userId  当前用户ID
     * @return 搜索结果
     */
    PageResult<DocSearchItemVO> search(DocSearchQueryRequest request, Long userId);
}
