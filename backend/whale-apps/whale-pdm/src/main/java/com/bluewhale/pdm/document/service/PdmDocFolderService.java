package com.bluewhale.pdm.document.service;

import com.bluewhale.pdm.document.dto.PdmDocFolderSaveRequest;
import com.bluewhale.pdm.document.vo.PdmDocFolderVO;

import java.util.List;

/**
 * 图文档目录服务
 *
 * @author BlueWhale
 */
public interface PdmDocFolderService {

    /**
     * 查询目录树
     *
     * @return 目录树
     */
    List<PdmDocFolderVO> tree();

    /**
     * 新建目录
     *
     * @param request 保存请求
     * @return 目录视图
     */
    PdmDocFolderVO create(PdmDocFolderSaveRequest request);

    /**
     * 更新目录（支持改名与移动）
     *
     * @param id      目录 ID
     * @param request 保存请求
     * @return 目录视图
     */
    PdmDocFolderVO update(Long id, PdmDocFolderSaveRequest request);

    /**
     * 删除空目录
     *
     * @param id 目录 ID
     */
    void delete(Long id);

    /**
     * 查询目录详情并校验存在
     *
     * @param id 目录 ID
     * @return 目录视图
     */
    PdmDocFolderVO get(Long id);

    /**
     * 查询目录及其全部子目录 ID
     *
     * @param folderId 目录 ID
     * @return 目录 ID 集合
     */
    List<Long> subtreeFolderIds(Long folderId);
}
