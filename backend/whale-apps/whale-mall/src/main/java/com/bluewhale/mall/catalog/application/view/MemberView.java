package com.bluewhale.mall.catalog.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员视图。
 *
 * @author BlueWhale
 */
@Data
public class MemberView {

    /** 主键 */
    private Long id;

    /** 平台用户 ID */
    private Long userId;

    /** 会员号 */
    private String memberNo;

    /** 会员等级 */
    private String levelCode;

    /** 有效期至（空 = 长期） */
    private LocalDateTime validTo;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
