package com.bluewhale.crm.customer.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 客户新建 / 编辑命令。
 *
 * @author BlueWhale
 */
@Data
public class CustomerSaveCommand {

    /** 客户编码；为空时系统生成 */
    private String customerCode;

    /** 客户名称 */
    @NotBlank(message = "客户名称不能为空")
    private String name;

    /** 客户简称 */
    private String shortName;

    /** 来源 */
    private String source;

    /** 行业 */
    private String industry;

    /** 联系地址 */
    private String address;

    /** 标签 */
    private String tags;

    /** 官网 */
    private String website;

    /** 企业规模 */
    private String scale;

    /** 纳税人识别号 */
    private String taxNo;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区县 */
    private String region;

    /** 等级 */
    private String level;

    /** 负责人（仅全量数据范围可指定） */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 是否已确认疑似重复（软冲突放行） */
    private Boolean confirmedDuplicate;
}
