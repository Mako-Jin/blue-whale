package com.bluewhale.config.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 配置项回滚请求
 *
 * @author BlueWhale
 */
@Data
public class ConfigItemRollbackRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 回滚目标版本号（该版本的取值将作为最新值，版本号继续 +1） */
    @NotNull(message = "目标版本号不能为空")
    private Integer targetVersion;
}
