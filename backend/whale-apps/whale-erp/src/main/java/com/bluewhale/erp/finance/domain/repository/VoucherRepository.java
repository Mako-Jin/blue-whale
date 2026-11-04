package com.bluewhale.erp.finance.domain.repository;

import com.bluewhale.erp.finance.domain.model.Voucher;
import com.bluewhale.erp.finance.domain.model.VoucherLine;
import com.bluewhale.erp.finance.domain.model.VoucherQuery;

import java.util.List;
import java.util.Optional;

/**
 * 凭证仓储。
 *
 * @author BlueWhale
 */
public interface VoucherRepository {

    /**
     * 分页查询凭证。
     *
     * @param query 查询条件
     * @return 凭证列表（不含分录）
     */
    List<Voucher> page(VoucherQuery query);

    /**
     * 统计凭证数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(VoucherQuery query);

    /**
     * 按主键查询（含分录）。
     *
     * @param id 主键
     * @return 凭证
     */
    Optional<Voucher> findById(Long id);

    /**
     * 按来源单据查询凭证（自动记账幂等）。
     *
     * @param sourceType 来源类型
     * @param sourceId 来源单据号
     * @return 凭证
     */
    Optional<Voucher> findBySource(String sourceType, String sourceId);

    /**
     * 生成期间内下一个凭证号。
     *
     * @param periodCode 期间编码
     * @return 凭证号
     */
    String nextVoucherNo(String periodCode);

    /**
     * 新增凭证（含分录）。
     *
     * @param voucher 凭证
     */
    void insert(Voucher voucher);

    /**
     * 更新凭证头（状态与红冲关系）。
     *
     * @param voucher 凭证
     */
    void update(Voucher voucher);

    /**
     * 查询已记账（含已红冲）凭证的分录，用于账簿与余额。
     *
     * @param accountCode 科目编码，为空表示全部
     * @param fromPeriod 起始期间
     * @param toPeriod 结束期间
     * @return 分录列表
     */
    List<VoucherLine> listPostedLines(String accountCode, String fromPeriod, String toPeriod);
}
