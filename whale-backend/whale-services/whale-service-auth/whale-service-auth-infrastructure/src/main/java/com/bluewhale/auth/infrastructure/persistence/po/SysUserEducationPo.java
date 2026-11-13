package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户教育经历子表实体（权限中心扩展 M3：对应 {@code sys_user_education}，多条有序）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_user_education")
public class SysUserEducationPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID */
    private Long tenantId;
    /** 用户 ID */
    private Long userId;
    /** 学校名称 */
    private String school;
    /** 专业 */
    private String major;
    /** 学历：DOCTOR 硕博 / MASTER 硕士 / BACHELOR 本科 / ASSOCIATE 大专 / OTHER 其他 */
    private String degree;
    /** 入学时间 */
    private LocalDate startDate;
    /** 毕业时间 */
    private LocalDate endDate;
    /** 排序号（倒序展示） */
    private Integer sortNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
