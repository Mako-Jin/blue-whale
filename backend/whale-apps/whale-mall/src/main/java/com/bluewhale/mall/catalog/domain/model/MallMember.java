package com.bluewhale.mall.catalog.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商城会员登记。
 *
 * <p>会员价（{@code PriceType.MEMBER}）的生效依据：零售形态下单时，
 * 买家在本表登记且「启用 + 有效期内」即按会员价目成交。等级字段先落
 * {@code GENERAL} 单值——价差体系要按等级展开时再加档，不必现在设计。</p>
 *
 * @author BlueWhale
 */
@Data
public class MallMember {

    /** 会员号最大长度 */
    public static final int MEMBER_NO_MAX_LENGTH = 64;

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 255;

    /** 主键 */
    private Long id;

    /** 平台用户 ID */
    private Long userId;

    /** 会员号（可空） */
    private String memberNo;

    /** 会员等级 */
    private String levelCode;

    /** 有效期至（空 = 长期） */
    private LocalDateTime validTo;

    /** 状态 */
    private MemberStatus status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验并规整会员字段。
     */
    public void validate() {
        if (userId == null || userId <= 0) {
            throw MallErrorCode.exception(MallErrorCode.MEMBER_INVALID, "会员必须绑定有效用户");
        }
        if (memberNo != null) {
            memberNo = memberNo.trim();
            if (memberNo.length() > MEMBER_NO_MAX_LENGTH) {
                throw MallErrorCode.exception(MallErrorCode.MEMBER_INVALID,
                        "会员号不得超过 " + MEMBER_NO_MAX_LENGTH + " 字符");
            }
            if (memberNo.isEmpty()) {
                memberNo = null;
            }
        }
        if (remark != null && remark.length() > REMARK_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.MEMBER_INVALID,
                    "备注不得超过 " + REMARK_MAX_LENGTH + " 字符");
        }
        if (levelCode == null || levelCode.isBlank()) {
            levelCode = "GENERAL";
        }
        levelCode = levelCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (status == null) {
            status = MemberStatus.ACTIVE;
        }
    }

    /**
     * 在指定时刻是否享受会员价（启用 + 有效期内）。
     *
     * @param now 当前时间
     * @return 生效返回 true
     */
    public boolean effective(LocalDateTime now) {
        if (status != null && status != MemberStatus.ACTIVE) {
            return false;
        }
        return validTo == null || now == null || !now.isAfter(validTo);
    }
}
