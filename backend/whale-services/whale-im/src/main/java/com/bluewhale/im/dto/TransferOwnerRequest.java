package com.bluewhale.im.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 群主转让请求。
 *
 * @author BlueWhale
 */
@Data
public class TransferOwnerRequest {

    /** 新群主用户ID（必须是群成员） */
    @NotNull(message = "targetUserId 不能为空")
    private Long targetUserId;
}
