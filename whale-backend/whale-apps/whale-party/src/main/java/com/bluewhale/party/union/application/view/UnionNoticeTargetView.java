package com.bluewhale.party.union.application.view;

import lombok.Data;

/**
 * 工会活动通知接收人。
 *
 * @author BlueWhale
 */
@Data
public class UnionNoticeTargetView {

    /** 会员 ID */
    private Long memberId;

    /** 会员姓名 */
    private String memberName;

    /** 用户 ID（必不为空） */
    private Long userId;
}
