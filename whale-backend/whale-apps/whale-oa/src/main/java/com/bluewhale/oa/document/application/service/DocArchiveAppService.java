package com.bluewhale.oa.document.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.document.application.view.DocumentViews;
import com.bluewhale.oa.document.domain.model.DocQuery;

/**
 * 公文归档应用服务。
 *
 * @author BlueWhale
 */
public interface DocArchiveAppService {

    /**
     * 分页查询归档映射。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<DocumentViews.ArchiveRef> page(DocQuery query);

    /**
     * 查询单据归档映射。
     *
     * @param docType 单据类型
     * @param docId   单据ID
     * @return 归档映射；不存在返回 {@code null}
     */
    DocumentViews.ArchiveRef find(String docType, Long docId);

    /**
     * 重试归档。
     *
     * @param id 归档映射ID
     * @return 归档映射
     */
    DocumentViews.ArchiveRef retry(Long id);

    /**
     * 推送公文到档案服务（幂等）。
     *
     * @param docType 单据类型
     * @param docId   单据ID
     * @return 档案条目ID
     */
    String archive(String docType, Long docId);
}
