package com.bluewhale.doc.application.service;

import com.bluewhale.doc.interfaces.dto.DocPageDuplicateRequest;
import com.bluewhale.doc.interfaces.vo.DocPageVO;

/**
 * 页面复制应用服务（M3）。
 *
 * @author BlueWhale
 */
public interface DocPageCopyService {

    /**
     * 复制页面（可含子树）到目标位置。
     *
     * @param pageId  源页面ID
     * @param request 复制请求
     * @param userId  当前用户ID
     * @return 新页面详情
     */
    DocPageVO duplicate(Long pageId, DocPageDuplicateRequest request, Long userId);
}
