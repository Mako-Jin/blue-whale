package com.bluewhale.doc.interfaces.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建登录内分享请求（M3）。
 *
 * @author BlueWhale
 */
@Data
public class DocPageShareCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 有效期天数；0 表示不限期，缺省取服务配置 */
    @Min(value = 0, message = "有效期不能为负数")
    @Max(value = 365, message = "有效期不能超过 365 天")
    private Integer expireDays;
}
