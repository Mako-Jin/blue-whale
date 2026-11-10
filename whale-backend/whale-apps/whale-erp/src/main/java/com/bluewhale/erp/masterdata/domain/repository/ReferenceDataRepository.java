package com.bluewhale.erp.masterdata.domain.repository;

import com.bluewhale.erp.masterdata.domain.model.Currency;
import com.bluewhale.erp.masterdata.domain.model.TaxCode;
import com.bluewhale.erp.masterdata.domain.model.Uom;

import java.util.List;

/**
 * 参考数据仓储（计量单位 / 币种 / 税码）。
 *
 * @author BlueWhale
 */
public interface ReferenceDataRepository {

    /**
     * 查询启用的计量单位。
     *
     * @return 计量单位列表
     */
    List<Uom> listUoms();

    /**
     * 查询启用的币种。
     *
     * @return 币种列表
     */
    List<Currency> listCurrencies();

    /**
     * 查询启用的税码。
     *
     * @return 税码列表
     */
    List<TaxCode> listTaxCodes();
}
