package com.bluewhale.pdm.collab.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.collab.dto.PdmClbReviewCloseRequest;
import com.bluewhale.pdm.collab.dto.PdmClbReviewQueryRequest;
import com.bluewhale.pdm.collab.dto.PdmClbReviewSaveRequest;
import com.bluewhale.pdm.collab.vo.PdmClbReviewVO;

/**
 * 研发评审服务
 *
 * @author BlueWhale
 */
public interface PdmClbReviewService {

    /**
     * 分页查询评审
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<PdmClbReviewVO> page(PdmClbReviewQueryRequest request);

    /**
     * 查询评审详情
     *
     * @param id 评审 ID
     * @return 评审视图
     */
    PdmClbReviewVO detail(Long id);

    /**
     * 新建评审（草稿）
     *
     * @param request 保存请求
     * @param userId  当前用户
     * @return 评审视图
     */
    PdmClbReviewVO create(PdmClbReviewSaveRequest request, Long userId);

    /**
     * 更新草稿评审
     *
     * @param id      评审 ID
     * @param request 保存请求
     * @param userId  当前用户
     * @return 评审视图
     */
    PdmClbReviewVO update(Long id, PdmClbReviewSaveRequest request, Long userId);

    /**
     * 关闭评审并写入结论
     *
     * @param id      评审 ID
     * @param request 关闭请求
     * @param userId  当前用户
     * @return 评审视图
     */
    PdmClbReviewVO close(Long id, PdmClbReviewCloseRequest request, Long userId);

    /**
     * 删除草稿评审
     *
     * @param id 评审 ID
     */
    void delete(Long id);
}
