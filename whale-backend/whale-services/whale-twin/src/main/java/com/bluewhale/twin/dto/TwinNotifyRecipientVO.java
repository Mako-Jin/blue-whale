package com.bluewhale.twin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知接收人候选项（Wave 6.11 M2）：接收人选择器的数据源。
 *
 * <p>只是「足够让人选」的最小字段，来自 auth 内网通讯录（手机号/邮箱在源头已脱敏，
 * 这里也不再透出）——孪生侧只需要知道选的是谁。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwinNotifyRecipientVO {

    /** 用户ID（字符串化，避免前端长整型精度丢失） */
    private String id;

    /** 展示名（昵称优先，昵称为空回落账号名） */
    private String name;

    /** 账号名（同名时便于区分） */
    private String username;

    /** 部门名（可空） */
    private String deptName;
}
