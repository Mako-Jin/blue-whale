package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 品牌配置响应（权限中心扩展 M0）。
 *
 * @author BlueWhale
 */
@Data
public class BrandConfigResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String targetType;
    private Long targetId;
    private String domain;
    private String brandName;
    private String logoUrl;
    private String loginTitle;
    private String institutionName;
    private String copyright;
    private String primaryColor;
    private String backgroundUrl;
    private Integer priority;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
