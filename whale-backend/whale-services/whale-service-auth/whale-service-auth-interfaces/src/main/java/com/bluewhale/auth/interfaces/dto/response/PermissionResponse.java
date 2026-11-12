package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限点响应
 *
 * @author BlueWhale
 */
@Data
public class PermissionResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private String permCode;
    private String permName;
    private String permType;
    private String path;
    private String method;
    private Integer sortNo;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
