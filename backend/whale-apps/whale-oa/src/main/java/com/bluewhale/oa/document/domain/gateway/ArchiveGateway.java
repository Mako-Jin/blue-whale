package com.bluewhale.oa.document.domain.gateway;

/**
 * 档案服务网关。
 *
 * @author BlueWhale
 */
public interface ArchiveGateway {

    /**
     * 推送公文快照入档（档案服务按 (sourceType, sourceId) 幂等）。
     *
     * @param command 归档命令
     * @return 档案条目ID
     */
    String archive(ArchivePushCommand command);
}
