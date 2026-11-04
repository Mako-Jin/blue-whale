package com.bluewhale.erp.scm.domain.port;

import java.util.Optional;

/**
 * 供应商目录端口：供应链只读取供应商快照。
 *
 * @author BlueWhale
 */
public interface SupplierCatalog {

    /**
     * 按主键查询供应商。
     *
     * @param id 供应商主键
     * @return 供应商快照
     */
    Optional<SupplierSnapshot> findById(Long id);
}
