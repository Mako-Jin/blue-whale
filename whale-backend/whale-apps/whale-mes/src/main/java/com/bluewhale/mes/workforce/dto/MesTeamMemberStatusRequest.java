package com.bluewhale.mes.workforce.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 班组成员启停请求
 *
 * @author BlueWhale
 */
@Data
public class MesTeamMemberStatusRequest {

    /** 是否启用：true 启用 / false 停用 */
    @NotNull(message = "{validation.mes.mes-team-member-status.enabled-required}")
    private Boolean enabled;
}
