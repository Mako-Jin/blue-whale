package com.bluewhale.pdm.document.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.document.dto.PdmDocDocumentQueryRequest;
import com.bluewhale.pdm.document.dto.PdmDocDocumentSaveRequest;
import com.bluewhale.pdm.document.support.PdmDocDownload;
import com.bluewhale.pdm.document.support.PdmDocUploadFile;
import com.bluewhale.pdm.document.vo.PdmDocDocumentVO;
import com.bluewhale.pdm.document.vo.PdmDocVersionVO;

import java.util.List;

/**
 * 图文档服务
 *
 * @author BlueWhale
 */
public interface PdmDocDocumentService {

    /**
     * 分页查询文档
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<PdmDocDocumentVO> page(PdmDocDocumentQueryRequest request);

    /**
     * 查询文档详情
     *
     * @param id 文档 ID
     * @return 文档视图
     */
    PdmDocDocumentVO detail(Long id);

    /**
     * 新建文档
     *
     * @param request  保存请求
     * @param userId   当前用户
     * @param userName 当前用户姓名
     * @return 文档视图
     */
    PdmDocDocumentVO create(PdmDocDocumentSaveRequest request, Long userId, String userName);

    /**
     * 更新文档元数据
     *
     * @param id       文档 ID
     * @param request  保存请求
     * @param userId   当前用户
     * @param canManage 是否可管理他人文档
     * @return 文档视图
     */
    PdmDocDocumentVO update(Long id, PdmDocDocumentSaveRequest request, Long userId, boolean canManage);

    /**
     * 删除文档（仅限无已发布版本）
     *
     * @param id        文档 ID
     * @param userId    当前用户
     * @param canManage 是否可管理他人文档
     */
    void delete(Long id, Long userId, boolean canManage);

    /**
     * 查询文档版本列表
     *
     * @param documentId 文档 ID
     * @return 版本列表
     */
    List<PdmDocVersionVO> versions(Long documentId);

    /**
     * 上传新草稿版本
     *
     * @param documentId 文档 ID
     * @param file       上传文件
     * @param changeNote 变更说明
     * @param userId     当前用户
     * @param canManage  是否可管理他人文档
     * @return 版本视图
     */
    PdmDocVersionVO uploadVersion(Long documentId, PdmDocUploadFile file, String changeNote,
                                  Long userId, boolean canManage);

    /**
     * 检出文档
     *
     * @param documentId 文档 ID
     * @param userId     当前用户
     * @return 文档视图
     */
    PdmDocDocumentVO checkOut(Long documentId, Long userId);

    /**
     * 检入文档（生成新草稿并释放检出锁）
     *
     * @param documentId 文档 ID
     * @param file       上传文件
     * @param changeNote 变更说明
     * @param userId     当前用户
     * @return 版本视图
     */
    PdmDocVersionVO checkIn(Long documentId, PdmDocUploadFile file, String changeNote, Long userId);

    /**
     * 发布草稿版本
     *
     * @param documentId 文档 ID
     * @param versionId  版本 ID
     * @param userId     当前用户
     * @param canManage  是否可管理他人文档
     * @return 版本视图
     */
    PdmDocVersionVO release(Long documentId, Long versionId, Long userId, boolean canManage);

    /**
     * 以历史版本为基线回滚生成新草稿
     *
     * @param documentId 文档 ID
     * @param versionId  历史版本 ID
     * @param userId     当前用户
     * @param canManage  是否可管理他人文档
     * @return 新版本视图
     */
    PdmDocVersionVO rollback(Long documentId, Long versionId, Long userId, boolean canManage);

    /**
     * 下载指定版本内容
     *
     * @param documentId 文档 ID
     * @param versionId  版本 ID
     * @return 下载内容
     */
    PdmDocDownload download(Long documentId, Long versionId);
}
