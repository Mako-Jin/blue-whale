package com.bluewhale.doc.interfaces.dto;

import com.bluewhale.doc.domain.model.DocMemberRole;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量添加/邀请空间成员。
 *
 * @author BlueWhale
 */
@Data
public class DocMemberAddRequest {

    /** 用户ID列表 */
    @NotEmpty(message = "请选择至少一个用户")
    private List<@NotNull Long> userIds;

    /** 授予角色：ADMIN / EDITOR / READER（OWNER 通过成员更新转让） */
    @NotNull(message = "成员角色不能为空")
    private DocMemberRole role;
}
