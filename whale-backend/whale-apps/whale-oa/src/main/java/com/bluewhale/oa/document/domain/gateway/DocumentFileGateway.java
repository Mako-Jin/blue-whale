package com.bluewhale.oa.document.domain.gateway;

/**
 * 文件服务网关（公文附件下载）。
 *
 * @author BlueWhale
 */
public interface DocumentFileGateway {

    /**
     * 读取文件内容。
     *
     * @param fileId 文件ID
     * @return 文件内容
     */
    byte[] content(Long fileId);
}
