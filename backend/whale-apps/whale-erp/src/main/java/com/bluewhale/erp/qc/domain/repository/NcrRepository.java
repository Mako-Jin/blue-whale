package com.bluewhale.erp.qc.domain.repository;

import com.bluewhale.erp.qc.domain.model.Nonconformity;
import com.bluewhale.erp.qc.domain.model.QcQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 不合格品单仓储。
 *
 * @author BlueWhale
 */
public interface NcrRepository {

    /**
     * 分页查询不合格品单。
     *
     * @param query 查询条件
     * @return 不合格品单列表
     */
    List<Nonconformity> page(QcQuery query);

    /**
     * 统计不合格品单数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(QcQuery query);

    /**
     * 按主键查询不合格品单。
     *
     * @param id 主键
     * @return 不合格品单
     */
    Optional<Nonconformity> findById(Long id);

    /**
     * 按来源查询不合格品单，用于 FAILED 检验幂等生成。
     *
     * @param sourceType 来源类型
     * @param sourceNo 来源单据号
     * @return 不合格品单
     */
    Optional<Nonconformity> findBySource(String sourceType, String sourceNo);

    /**
     * 生成不合格品单号。
     *
     * @param date 建单日期
     * @return 不合格品单号
     */
    String nextNcrNo(LocalDate date);

    /**
     * 新增不合格品单。
     *
     * @param nonconformity 不合格品单
     */
    void insert(Nonconformity nonconformity);

    /**
     * 更新不合格品单状态与处置信息。
     *
     * @param nonconformity 不合格品单
     */
    void update(Nonconformity nonconformity);
}
