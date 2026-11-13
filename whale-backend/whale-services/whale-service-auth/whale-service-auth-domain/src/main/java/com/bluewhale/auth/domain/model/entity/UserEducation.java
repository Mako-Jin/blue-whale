package com.bluewhale.auth.domain.model.entity;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户教育经历实体（对应 {@code sys_user_education}，权限中心扩展 M3）。
 *
 * <p>多条有序，只允许通过 {@code UserProfileAggregate} 整体替换。</p>
 *
 * @param id 主键（新建时为 {@code null}）
 * @param school 学校名称
 * @param major 专业（可空）
 * @param degree 学历编码（可空）
 * @param startDate 入学时间（可空）
 * @param endDate 毕业时间（可空）
 * @param sortNo 排序号
 * @param createTime 创建时间
 * @param updateTime 更新时间
 * @author BlueWhale
 */
public record UserEducation(Long id, String school, String major, String degree,
                            LocalDate startDate, LocalDate endDate, Integer sortNo,
                            LocalDateTime createTime, LocalDateTime updateTime) implements Entity<Long> {

    /**
     * 工厂方法：新建教育经历
     *
     * @param school 学校名称
     * @param major 专业
     * @param degree 学历编码
     * @param startDate 入学时间
     * @param endDate 毕业时间
     * @param sortNo 排序号
     * @return 教育经历实体
     */
    public static UserEducation create(String school, String major, String degree,
                                       LocalDate startDate, LocalDate endDate, Integer sortNo) {
        if (school == null || school.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "学校名称不能为空");
        }
        return new UserEducation(null, school.trim(), major, degree, startDate, endDate,
                sortNo == null ? 0 : sortNo, LocalDateTime.now(), LocalDateTime.now());
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
