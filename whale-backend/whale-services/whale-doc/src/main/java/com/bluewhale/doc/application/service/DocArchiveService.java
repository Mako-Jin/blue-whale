package com.bluewhale.doc.application.service;

/**
 * 页面归档快照应用服务（M3）。
 *
 * @author BlueWhale
 */
public interface DocArchiveService {

    /**
     * 生成页面快照并推送档案服务。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @param userId   操作人ID
     * @return 是否新入档
     */
    boolean archiveSnapshot(Long tenantId, Long pageId, Long userId);
}
