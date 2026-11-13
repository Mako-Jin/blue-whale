package com.bluewhale.file.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 存储插件响应。
 *
 * @author BlueWhale
 */
@Data
public class StoragePluginResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;
    private String displayName;
    private Boolean presignedSupported;
    private Boolean active;
}
