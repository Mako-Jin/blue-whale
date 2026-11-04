package com.bluewhale.erp.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ERP 自动记账科目映射配置。
 *
 * <p>采购收货、销售发货生成的凭证使用这里的科目编码；决策 D5（科目映射）确认后
 * 只需改配置，不需要改代码。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "erp.finance.account")
public class ErpFinanceAccountProperties {

    /** 存货科目（默认 1405） */
    private String inventory = "1405";

    /** 应付账款科目（默认 2202） */
    private String payable = "2202";

    /** 应收账款科目（默认 1122） */
    private String receivable = "1122";

    /** 主营业务收入科目（默认 6001） */
    private String revenue = "6001";

    /** 主营业务成本科目（默认 6401） */
    private String cost = "6401";

    /** 待处理财产损溢科目（默认 1901，盘盈/盘亏使用） */
    private String stockVariance = "1901";
}
