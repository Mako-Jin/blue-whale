package com.bluewhale.erp.mfg.domain.port;

import com.bluewhale.erp.mfg.domain.model.BomSnapshot;

/**
 * PDM BOM 快照只读端口。
 *
 * @author BlueWhale
 */
public interface BomCatalog {

    /**
     * 拉取已发布 BOM 快照。
     *
     * @param bomNo BOM 编号
     * @param versionNo 版本号
     * @return 快照
     */
    BomSnapshot fetch(String bomNo, String versionNo);
}
