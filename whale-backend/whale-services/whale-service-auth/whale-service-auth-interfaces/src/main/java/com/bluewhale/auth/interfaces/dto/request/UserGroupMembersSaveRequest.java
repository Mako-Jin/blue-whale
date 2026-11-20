package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 用户组成员保存请求（权限中心扩展 M3）。
 *
 * <p>全量替换口径：{@code memberUserIds} 为空列表表示清空成员。</p>
 *
 * @author BlueWhale
 */
@Data
public class UserGroupMembersSaveRequest {

    /** 成员用户 ID 列表（空列表表示清空） */
    @NotNull(message = "{validation.auth.group.member-ids-required}")
    private List<Long> memberUserIds;
}
