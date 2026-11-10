package com.bluewhale.erp.scm.domain.repository;

import com.bluewhale.erp.scm.domain.model.SalesQuotation;
import com.bluewhale.erp.scm.domain.model.ScmQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 销售报价单仓储。
 *
 * @author BlueWhale
 */
public interface SalesQuotationRepository {

    /**
     * 分页查询销售报价单。
     *
     * @param query 查询条件
     * @return 报价单列表
     */
    List<SalesQuotation> page(ScmQuery query);

    /**
     * 统计销售报价单数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(ScmQuery query);

    /**
     * 按主键查询报价单（含分录）。
     *
     * @param id 主键
     * @return 报价单
     */
    Optional<SalesQuotation> findById(Long id);

    /**
     * 新增报价单。
     *
     * @param quotation 报价单
     */
    void insert(SalesQuotation quotation);

    /**
     * 更新报价单头。
     *
     * @param quotation 报价单
     */
    void update(SalesQuotation quotation);

    /**
     * 生成报价单号。
     *
     * @param docDate 报价日期
     * @return 单号
     */
    String nextDocNo(LocalDate docDate);
}
