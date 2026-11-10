package com.bluewhale.mall.catalog.application.command;

import com.bluewhale.mall.catalog.domain.model.MallMember;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员保存命令。
 *
 * @author BlueWhale
 */
@Data
public class MemberSaveCommand {

    /** 主键（按用户登记新建时可空） */
    private Long id;

    /** 平台用户 ID */
    private Long userId;

    /** 会员号（可空） */
    private String memberNo;

    /** 会员等级（空默认 GENERAL） */
    private String levelCode;

    /** 有效期至（空 = 长期） */
    private LocalDateTime validTo;

    /** 状态：ACTIVE / DISABLED（空默认 ACTIVE） */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 转领域对象。
     *
     * @return 会员领域对象
     */
    public MallMember toDomain() {
        MallMember member = new MallMember();
        member.setId(id);
        member.setUserId(userId);
        member.setMemberNo(memberNo);
        member.setLevelCode(levelCode);
        member.setValidTo(validTo);
        member.setStatus(com.bluewhale.mall.catalog.domain.model.MemberStatus.parse(status));
        member.setRemark(remark);
        return member;
    }
}
