package com.bluewhale.crm.service.domain.port;

import java.util.Optional;

/**
 * whale-doc 文档详情端口。
 *
 * @author BlueWhale
 */
public interface DocPagePort {

    /**
     * 读取公开已发布文档详情。
     *
     * @param docId 文档 ID
     * @return 文档快照；不可用或服务异常时为空
     */
    Optional<DocPageSnapshot> findPublicPage(Long docId);

    /**
     * 文档快照。
     *
     * @param title 文档标题
     * @param revision 文档版本
     */
    record DocPageSnapshot(String title, Integer revision) {
    }
}
