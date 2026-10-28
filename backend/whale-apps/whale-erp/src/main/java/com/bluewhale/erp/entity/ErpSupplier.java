package com.bluewhale.erp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 供应商主数据实体，对应表 {@code erp_supplier}。
 *
 * <p>供应商准入/资质/绩效的完整流程属于 SRM 域；本表先承接 ERP 所需的供应商主数据，
 * 合同域「相对方」仅保存签署快照，不复制本表。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_supplier")
public class ErpSupplier extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 供应商编码（租户内唯一） */
    private String code;

    /** 供应商名称 */
    private String name;

    /** 供应商简称 */
    private String shortName;

    /** 供应商分类：MATERIAL / SERVICE / OUTSOURCE / LOGISTICS / OTHER */
    private String category;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 联系邮箱 */
    private String contactEmail;

    /** 纳税人识别号 */
    private String taxNo;

    /** 开户银行 */
    private String bankName;

    /** 银行账号 */
    private String bankAccount;

    /** 地址 */
    private String address;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 供应商评级：A / B / C / D */
    private String rating;

    /** 备注 */
    private String remark;
}
