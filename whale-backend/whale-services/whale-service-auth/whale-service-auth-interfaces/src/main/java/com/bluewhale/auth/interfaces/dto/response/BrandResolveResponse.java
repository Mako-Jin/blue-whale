package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录页品牌解析响应（免登录，权限中心扩展 M0）。
 *
 * <p>只暴露渲染登录页所需字段，不返回作用域、优先级等管理侧元数据。</p>
 *
 * @author BlueWhale
 */
@Data
public class BrandResolveResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String brandName;
    private String logoUrl;
    private String loginTitle;
    private String institutionName;
    private String copyright;
    private String primaryColor;
    private String backgroundUrl;
}
