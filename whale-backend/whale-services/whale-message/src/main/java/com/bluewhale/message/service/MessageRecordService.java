package com.bluewhale.message.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.message.dto.MessageRecordQueryRequest;
import com.bluewhale.message.vo.MessageRecordVO;

/**
 * 消息记录查询服务。
 *
 * @author BlueWhale
 */
public interface MessageRecordService {

    /**
     * 分页查询消息记录。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MessageRecordVO> page(MessageRecordQueryRequest request);

    /**
     * 查询消息详情（含投递明细）。
     *
     * @param id 消息ID
     * @return 消息详情
     */
    MessageRecordVO detail(Long id);
}
