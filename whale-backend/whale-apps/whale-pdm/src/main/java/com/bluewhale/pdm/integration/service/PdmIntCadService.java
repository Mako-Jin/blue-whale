package com.bluewhale.pdm.integration.service;

import com.bluewhale.pdm.document.support.PdmDocUploadFile;
import com.bluewhale.pdm.document.support.PdmDocViewer;
import com.bluewhale.pdm.document.vo.PdmDocVersionVO;
import com.bluewhale.pdm.integration.dto.PdmIntCadEbomImportRequest;
import com.bluewhale.pdm.part.vo.PdmBomVO;

/**
 * CAD 集成服务
 *
 * <p>承接外部 CAD 工具的检入与装配 EBOM 导入；以工具侧幂等键去重，
 * 重复调用不生成新版本、不重复建 BOM。</p>
 *
 * @author BlueWhale
 */
public interface PdmIntCadService {

    /**
     * CAD 检入文档新版本
     *
     * @param documentId 文档ID
     * @param changeNote 变更说明
     * @param externalKey 外部幂等键
     * @param upload     上传文件
     * @param viewer     访问者
     * @param operatorId 操作人
     * @return 版本视图
     */
    PdmDocVersionVO checkin(Long documentId, String changeNote, String externalKey, PdmDocUploadFile upload,
                            PdmDocViewer viewer, Long operatorId);

    /**
     * 导入 CAD 装配 EBOM 草稿
     *
     * @param request    导入请求
     * @param operatorId 操作人
     * @return BOM 视图
     */
    PdmBomVO importEbom(PdmIntCadEbomImportRequest request, Long operatorId);
}
