package com.bluewhale.erp.scm.domain.port;

import java.util.Optional;

/**
 * 物料目录端口：供应链只读取物料快照，不直接访问物料表。
 *
 * @author BlueWhale
 */
public interface ItemCatalog {

    /**
     * 按主键查询物料。
     *
     * @param id 物料主键
     * @return 物料快照
     */
    Optional<ItemSnapshot> findById(Long id);

    /**
     * 按编码查询物料。
     *
     * @param code 物料编码
     * @return 物料快照
     */
    Optional<ItemSnapshot> findByCode(String code);
}
