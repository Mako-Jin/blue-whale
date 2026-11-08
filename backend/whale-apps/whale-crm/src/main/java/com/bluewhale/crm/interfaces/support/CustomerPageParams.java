package com.bluewhale.crm.interfaces.support;

import com.bluewhale.crm.customer.domain.model.CustomerQuery;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户 / 公海分页查询参数。
 *
 * @author BlueWhale
 */
@Data
public class CustomerPageParams {

    /** 关键字（编码 / 名称 / 简称） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 等级 */
    private String level;

    /** 来源 */
    private String source;

    /** 行业 */
    private String industry;

    /** 负责人用户ID */
    private Long ownerId;

    /** 创建时间起 */
    private LocalDateTime createdFrom;

    /** 创建时间止 */
    private LocalDateTime createdTo;

    /** 当前页码 */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 10L;

    /**
     * 转换为领域查询对象。
     *
     * @return 客户查询条件
     */
    public CustomerQuery toQuery() {
        CustomerQuery query = new CustomerQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setLevel(level);
        query.setSource(source);
        query.setIndustry(industry);
        query.setOwnerId(ownerId);
        query.setCreatedFrom(createdFrom);
        query.setCreatedTo(createdTo);
        query.setCurrent(current == null || current < 1 ? 1L : current);
        query.setSize(size == null || size < 1 ? 10L : Math.min(size, 200L));
        return query;
    }
}
