package com.bluewhale.alert.service;

import com.bluewhale.alert.dto.AlertRecordQueryRequest;
import com.bluewhale.alert.vo.AlertRecordVO;
import com.bluewhale.common.core.result.PageResult;

/**
 * 告警记录服务
 *
 * @author BlueWhale
 */
public interface AlertRecordService {

    /**
     * 分页查询告警记录
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<AlertRecordVO> page(AlertRecordQueryRequest request);
}
