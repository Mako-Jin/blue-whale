package com.bluewhale.pdm.document.service;

import com.bluewhale.pdm.document.dto.PdmDocAclSaveRequest;
import com.bluewhale.pdm.document.support.PdmDocViewer;
import com.bluewhale.pdm.document.vo.PdmDocAclVO;

import java.util.List;

/**
 * 图文档 ACL 规则服务
 *
 * @author BlueWhale
 */
public interface PdmDocAclService {

    /**
     * 查询资源的 ACL 规则
     *
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param viewer       访问者
     * @return 规则列表
     */
    List<PdmDocAclVO> list(String resourceType, Long resourceId, PdmDocViewer viewer);

    /**
     * 新增规则（唯一键重复时幂等返回既有规则）
     *
     * @param request 保存请求
     * @param viewer  访问者
     * @return 规则视图
     */
    PdmDocAclVO save(PdmDocAclSaveRequest request, PdmDocViewer viewer);

    /**
     * 删除规则
     *
     * @param id     规则 ID
     * @param viewer 访问者
     */
    void delete(Long id, PdmDocViewer viewer);
}
