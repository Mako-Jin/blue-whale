package com.bluewhale.workflow.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 流程定义版本发布请求。
 *
 * @author BlueWhale
 */
@Data
public class DefinitionPublishRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 要发布的版本号（必填，须存在对应快照） */
    @NotNull(message = "版本号不能为空")
    private Integer version;
}
