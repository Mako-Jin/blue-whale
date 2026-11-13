package com.bluewhale.auth.domain.model.entity;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户工作经历实体（对应 {@code sys_user_employment}，权限中心扩展 M3）。
 *
 * <p>多条有序，只允许通过 {@code UserProfileAggregate} 整体替换；{@code endDate} 为空表示在职。</p>
 *
 * @param id 主键（新建时为 {@code null}）
 * @param company 公司名称
 * @param department 部门（可空）
 * @param position 职位（可空）
 * @param employmentType 用工性质编码（可空）
 * @param startDate 入职时间（可空）
 * @param endDate 离职时间（在职为 {@code null}）
 * @param sortNo 排序号
 * @param createTime 创建时间
 * @param updateTime 更新时间
 * @author BlueWhale
 */
public record UserEmployment(Long id, String company, String department, String position,
                             String employmentType, LocalDate startDate, LocalDate endDate,
                             Integer sortNo, LocalDateTime createTime, LocalDateTime updateTime)
        implements Entity<Long> {

    /**
     * 工厂方法：新建工作经历
     *
     * @param company 公司名称
     * @param department 部门
     * @param position 职位
     * @param employmentType 用工性质
     * @param startDate 入职时间
     * @param endDate 离职时间
     * @param sortNo 排序号
     * @return 工作经历实体
     */
    public static UserEmployment create(String company, String department, String position,
                                        String employmentType, LocalDate startDate, LocalDate endDate,
                                        Integer sortNo) {
        if (company == null || company.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "公司名称不能为空");
        }
        return new UserEmployment(null, company.trim(), department, position, employmentType,
                startDate, endDate, sortNo == null ? 0 : sortNo, LocalDateTime.now(), LocalDateTime.now());
    }

    /**
     * 判断是否在职（离职时间为空）
     *
     * @return 在职返回 {@code true}
     */
    public boolean isCurrent() {
        return endDate == null;
    }

    /**
     * 获取主键
     *
     * @return 主键（新建时为 {@code null}）
     */
    @Override
    public Long getId() {
        return id;
    }
}
