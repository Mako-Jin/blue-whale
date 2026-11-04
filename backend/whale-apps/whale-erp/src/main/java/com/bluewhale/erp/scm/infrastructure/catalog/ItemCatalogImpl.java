package com.bluewhale.erp.scm.infrastructure.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.erp.entity.ErpItem;
import com.bluewhale.erp.mapper.ErpItemMapper;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 物料目录实现：读取既有 {@code erp_item}，只暴露只读快照。
 *
 * <p>物料主数据迁移到 {@code masterdata} 上下文后，仅替换本实现即可。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ItemCatalogImpl implements ItemCatalog {

    private final ErpItemMapper itemMapper;

    @Override
    public Optional<ItemSnapshot> findById(Long id) {
        return Optional.ofNullable(itemMapper.selectById(id)).map(this::toSnapshot);
    }

    @Override
    public Optional<ItemSnapshot> findByCode(String code) {
        return Optional.ofNullable(itemMapper.selectOne(new LambdaQueryWrapper<ErpItem>()
                .eq(ErpItem::getCode, code)
                .last("limit 1"))).map(this::toSnapshot);
    }

    /**
     * 实体转快照。
     *
     * @param item 物料实体
     * @return 快照
     */
    private ItemSnapshot toSnapshot(ErpItem item) {
        ItemSnapshot snapshot = new ItemSnapshot();
        snapshot.setId(item.getId());
        snapshot.setCode(item.getCode());
        snapshot.setName(item.getName());
        snapshot.setStatus(item.getStatus());
        return snapshot;
    }
}
