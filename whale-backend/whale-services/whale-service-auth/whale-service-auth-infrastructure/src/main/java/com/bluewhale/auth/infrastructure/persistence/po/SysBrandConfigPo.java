package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 品牌配置实体（权限中心扩展 M0：对应 {@code sys_brand_config}）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_brand_config")
public class SysBrandConfigPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 作用域类型：PLATFORM / TENANT / COMPANY */
    private String targetType;
    /** 作用域 ID：PLATFORM 固定 0 */
    private Long targetId;
    /** 绑定域名：空串表示默认品牌 */
    private String domain;
    private String brandName;
    private String logoUrl;
    private String loginTitle;
    private String institutionName;
    private String copyright;
    private String primaryColor;
    private String backgroundUrl;
    /** 解析优先级：数字越小越优先 */
    private Integer priority;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
