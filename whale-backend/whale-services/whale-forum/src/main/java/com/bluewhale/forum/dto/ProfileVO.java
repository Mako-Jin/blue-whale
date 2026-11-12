package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 个人主页视图（F-07-01）。
 *
 * <p>只含本服务可得数据：头像 / 部门待 auth 通讯录、勋章属 P2，接口先稳定已字段，
 * 后续补齐只加不改。禁言只回类型与到期时间，<b>不回原因</b>——处置原因是治理信息。</p>
 *
 * @author BlueWhale
 */
@Data
public class ProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 鲸阶等级（从 1 起） */
    private Integer level;

    /** 鲸阶名称 */
    private String levelName;

    /** 下一级所需经验（已满级为 null） */
    private Long nextLevelExp;

    /** 经验总额 */
    private Long expTotal;

    /** 发帖数（可见状态） */
    private Long threadCount;

    /** 获花总数（帖级投花，不含楼层） */
    private Long likeCount;

    /** 精华数 */
    private Long essenceCount;

    /** 当前是否禁言/封号 */
    private Boolean banned;

    /** 处置类型：MUTE / BAN（未处置为 null） */
    private String banType;

    /** 处置到期时间（null 且 banned=true 表示永久） */
    private LocalDateTime banUntil;
}
