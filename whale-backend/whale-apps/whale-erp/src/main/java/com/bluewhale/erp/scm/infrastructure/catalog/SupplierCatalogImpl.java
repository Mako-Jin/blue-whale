package com.bluewhale.erp.scm.infrastructure.catalog;

import com.bluewhale.erp.entity.ErpSupplier;
import com.bluewhale.erp.mapper.ErpSupplierMapper;
import com.bluewhale.erp.scm.domain.port.SupplierCatalog;
import com.bluewhale.erp.scm.domain.port.SupplierSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 供应商目录实现：读取既有 {@code erp_supplier}。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class SupplierCatalogImpl implements SupplierCatalog {

    private final ErpSupplierMapper supplierMapper;

    @Override
    public Optional<SupplierSnapshot> findById(Long id) {
        return Optional.ofNullable(supplierMapper.selectById(id)).map(this::toSnapshot);
    }

    /**
     * 实体转快照。
     *
     * @param supplier 供应商实体
     * @return 快照
     */
    private SupplierSnapshot toSnapshot(ErpSupplier supplier) {
        SupplierSnapshot snapshot = new SupplierSnapshot();
        snapshot.setId(supplier.getId());
        snapshot.setCode(supplier.getCode());
        snapshot.setName(supplier.getName());
        snapshot.setStatus(supplier.getStatus());
        return snapshot;
    }
}
