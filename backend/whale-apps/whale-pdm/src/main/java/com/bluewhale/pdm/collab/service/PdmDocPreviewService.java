package com.bluewhale.pdm.collab.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.collab.vo.PdmDocPreviewJobVO;
import com.bluewhale.pdm.document.support.PdmDocDownload;
import com.bluewhale.pdm.document.support.PdmDocViewer;

/**
 * 在线预览服务
 *
 * @author BlueWhale
 */
public interface PdmDocPreviewService {

    /**
     * 创建（或复用）预览任务
     *
     * @param documentId  文档 ID
     * @param versionId   版本 ID
     * @param previewType 预览类型 2D/3D
     * @param viewer      访问者
     * @return 预览任务视图
     */
    PdmDocPreviewJobVO request(Long documentId, Long versionId, String previewType, PdmDocViewer viewer);

    /**
     * 重试预览任务
     *
     * @param jobId  任务 ID
     * @param viewer 访问者
     * @return 预览任务视图
     */
    PdmDocPreviewJobVO retry(Long jobId, PdmDocViewer viewer);

    /**
     * 分页查询预览任务
     *
     * @param documentId 文档 ID（可空）
     * @param versionId  版本 ID（可空）
     * @param current    页码
     * @param size       每页条数
     * @return 分页结果
     */
    PageResult<PdmDocPreviewJobVO> page(Long documentId, Long versionId, long current, long size);

    /**
     * 读取预览内容（成功任务的原文件或产物）
     *
     * @param jobId  任务 ID
     * @param viewer 访问者
     * @return 预览内容
     */
    PdmDocDownload content(Long jobId, PdmDocViewer viewer);
}
