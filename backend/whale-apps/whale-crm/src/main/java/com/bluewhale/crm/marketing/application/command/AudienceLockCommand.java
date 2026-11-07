package com.bluewhale.crm.marketing.application.command;

import com.bluewhale.crm.marketing.domain.model.AudienceCriteria;
import lombok.Data;

/**
 * 活动人群锁定命令。
 *
 * @author BlueWhale
 */
@Data
public class AudienceLockCommand {

    /** 人群筛选条件 */
    private AudienceCriteria criteria;
}
