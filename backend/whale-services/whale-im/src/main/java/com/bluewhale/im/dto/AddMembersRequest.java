package com.bluewhale.im.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 添加群成员请求（{@code POST /im/conversations/{id}/members}）。
 *
 * @author BlueWhale
 */
@Data
public class AddMembersRequest {

    /** 待添加的用户ID列表（单次最多 199 位，实际还受群成员上限约束） */
    @NotEmpty
    @Size(max = 199)
    private List<Long> memberIds;
}
