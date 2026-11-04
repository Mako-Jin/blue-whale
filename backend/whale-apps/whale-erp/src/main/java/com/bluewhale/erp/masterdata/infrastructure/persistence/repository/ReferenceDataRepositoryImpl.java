package com.bluewhale.erp.masterdata.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.erp.masterdata.domain.model.Currency;
import com.bluewhale.erp.masterdata.domain.model.TaxCode;
import com.bluewhale.erp.masterdata.domain.model.Uom;
import com.bluewhale.erp.masterdata.domain.repository.ReferenceDataRepository;
import com.bluewhale.erp.masterdata.infrastructure.persistence.entity.CurrencyEntity;
import com.bluewhale.erp.masterdata.infrastructure.persistence.entity.TaxCodeEntity;
import com.bluewhale.erp.masterdata.infrastructure.persistence.entity.UomEntity;
import com.bluewhale.erp.masterdata.infrastructure.persistence.mapper.CurrencyMapper;
import com.bluewhale.erp.masterdata.infrastructure.persistence.mapper.TaxCodeMapper;
import com.bluewhale.erp.masterdata.infrastructure.persistence.mapper.UomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 参考数据仓储实现（MyBatis-Plus，只读）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ReferenceDataRepositoryImpl implements ReferenceDataRepository {

    private final UomMapper uomMapper;

    private final CurrencyMapper currencyMapper;

    private final TaxCodeMapper taxCodeMapper;

    @Override
    public List<Uom> listUoms() {
        return uomMapper.selectList(new LambdaQueryWrapper<UomEntity>()
                        .eq(UomEntity::getStatus, "ENABLED")
                        .orderByAsc(UomEntity::getCode))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Currency> listCurrencies() {
        return currencyMapper.selectList(new LambdaQueryWrapper<CurrencyEntity>()
                        .eq(CurrencyEntity::getStatus, "ENABLED")
                        .orderByDesc(CurrencyEntity::getBaseFlag)
                        .orderByAsc(CurrencyEntity::getCode))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<TaxCode> listTaxCodes() {
        return taxCodeMapper.selectList(new LambdaQueryWrapper<TaxCodeEntity>()
                        .eq(TaxCodeEntity::getStatus, "ENABLED")
                        .orderByAsc(TaxCodeEntity::getCode))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 单位实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Uom toDomain(UomEntity entity) {
        Uom uom = new Uom();
        uom.setId(entity.getId());
        uom.setCode(entity.getCode());
        uom.setName(entity.getName());
        uom.setPrecisionNo(entity.getPrecisionNo());
        uom.setStatus(entity.getStatus());
        return uom;
    }

    /**
     * 币种实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Currency toDomain(CurrencyEntity entity) {
        Currency currency = new Currency();
        currency.setId(entity.getId());
        currency.setCode(entity.getCode());
        currency.setName(entity.getName());
        currency.setSymbol(entity.getSymbol());
        currency.setBaseFlag(entity.getBaseFlag());
        currency.setStatus(entity.getStatus());
        return currency;
    }

    /**
     * 税码实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private TaxCode toDomain(TaxCodeEntity entity) {
        TaxCode taxCode = new TaxCode();
        taxCode.setId(entity.getId());
        taxCode.setCode(entity.getCode());
        taxCode.setName(entity.getName());
        taxCode.setTaxRate(entity.getTaxRate());
        taxCode.setTaxType(entity.getTaxType());
        taxCode.setStatus(entity.getStatus());
        return taxCode;
    }
}
