package com.bluewhale.doc.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.doc.interfaces.vo.DocStarItemVO;
import com.bluewhale.doc.interfaces.vo.DocStarToggleVO;

/**
 * 页面收藏应用服务。
 *
 * @author BlueWhale
 */
public interface DocStarService {

    /**
     * 切换页面收藏状态。
     *
     * @param pageId 页面ID
     * @param userId 当前用户ID
     * @return 切换后的收藏状态
     */
    DocStarToggleVO toggle(Long pageId, Long userId);

    /**
     * 分页查询我的收藏。
     *
     * @param current 当前页码
     * @param size    每页条数
     * @param userId  当前用户ID
     * @return 收藏分页
     */
    PageResult<DocStarItemVO> page(long current, long size, Long userId);
}
