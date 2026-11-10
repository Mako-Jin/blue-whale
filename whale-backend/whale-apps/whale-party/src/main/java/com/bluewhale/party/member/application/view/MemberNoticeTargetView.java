package com.bluewhale.party.member.application.view;

import lombok.Data;

/**
 * 通知接收人视图（党员）。
 *
 * <p>只带 userId 与姓名：组织生活通知按支部群发，不需要把党员档案的敏感字段
 * 带进通知投递流程。</p>
 *
 * @author BlueWhale
 */
@Data
public class MemberNoticeTargetView {

    /** 党员档案 ID */
    private Long memberId;

    /** 党员姓名 */
    private String memberName;

    /** 用户 ID（必不为空，空账号的党员在服务层已被过滤） */
    private Long userId;
}
