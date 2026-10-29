package com.bluewhale.doc.interfaces.dto;

import com.bluewhale.doc.domain.model.DocMemberRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新成员角色。
 *
 * @author BlueWhale
 */
@Data
public class DocMemberRoleRequest {

    /** 新角色；OWNER 仅允许原 OWNER 操作并触发转让 */
    @NotNull(message = "成员角色不能为空")
    private DocMemberRole role;
}
