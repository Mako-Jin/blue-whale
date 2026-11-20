package com.bluewhale.auth.interfaces.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户续期请求。
 *
 * @author BlueWhale
 */
@Data
public class TenantRenewRequest {

    /** 新到期时间，必须晚于当前时间 */
    @NotNull(message = "{validation.auth.tenant.expire-time-required}")
    @Future(message = "{validation.auth.tenant.expire-time-future}")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;
}
