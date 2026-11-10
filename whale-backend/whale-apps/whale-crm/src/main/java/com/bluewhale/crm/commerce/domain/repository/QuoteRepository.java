package com.bluewhale.crm.commerce.domain.repository;

import com.bluewhale.crm.commerce.domain.model.Quote;
import com.bluewhale.crm.commerce.domain.model.QuoteLine;
import com.bluewhale.crm.commerce.domain.model.QuoteQuery;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报价仓储端口。
 *
 * @author BlueWhale
 */
public interface QuoteRepository {

    /**
     * 分页查询报价。
     *
     * @param query 查询条件
     * @return 报价列表
     */
    List<Quote> page(QuoteQuery query);

    /**
     * 统计报价总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(QuoteQuery query);

    /**
     * 按 ID 查询报价。
     *
     * @param id 报价 ID
     * @return 报价；不存在返回 {@code null}
     */
    Quote findById(Long id);

    /**
     * 查询报价行。
     *
     * @param quoteId 报价 ID
     * @return 行列表
     */
    List<QuoteLine> findLines(Long quoteId);

    /**
     * 插入报价头。
     *
     * @param quote 报价
     */
    void insert(Quote quote);

    /**
     * 绑定首版 ID。
     *
     * @param id 报价 ID
     * @param rootQuoteId 首版 ID
     */
    void bindRoot(Long id, Long rootQuoteId);

    /**
     * 全量替换报价行。
     *
     * @param quoteId 报价 ID
     * @param lines 行列表
     */
    void replaceLines(Long quoteId, List<QuoteLine> lines);

    /**
     * 更新报价头（乐观锁）。
     *
     * @param quote 报价
     * @return 更新成功返回 true
     */
    boolean update(Quote quote);

    /**
     * 条件更新审批结果（仅 PENDING_APPROVAL 命中）。
     *
     * @param quoteId 报价 ID
     * @param approved 是否通过
     * @param at 决策时间
     * @return 更新成功返回 true
     */
    boolean applyApprovalResult(Long quoteId, boolean approved, LocalDateTime at);

    /**
     * 查询指定编号前缀的最大序号。
     *
     * @param prefix 编号前缀
     * @return 最大序号；无记录返回 0
     */
    long maxSequence(String prefix);
}
