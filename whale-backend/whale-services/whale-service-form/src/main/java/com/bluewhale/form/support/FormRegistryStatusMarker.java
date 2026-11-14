package com.bluewhale.form.support;

import com.bluewhale.form.po.FormTableRegistryPo;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 物理表登记状态标记器。
 *
 * <p>发布失败时把已有登记置为「失败」并记录原因，使用独立事务提交，确保不会被发布方法的回滚吞掉；
 * 失败登记不参与数据读写，重新发布成功后恢复为「生效」。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class FormRegistryStatusMarker {

    /** 登记状态：发布失败 */
    public static final int STATUS_FAILED = 3;

    /** 失败原因最大长度 */
    private static final int MAX_ERROR_LENGTH = 500;

    private final FormTableRegistryMapper formTableRegistryMapper;

    /**
     * 标记登记为发布失败（独立事务）
     *
     * @param registryId 登记ID
     * @param errorMessage 失败原因
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(Long registryId, String errorMessage) {
        FormTableRegistryPo update = new FormTableRegistryPo();
        update.setId(registryId);
        update.setStatus(STATUS_FAILED);
        update.setLastError(truncate(errorMessage));
        formTableRegistryMapper.updateById(update);
    }

    /**
     * 截断失败原因，避免超出列宽
     *
     * @param errorMessage 原始失败原因
     * @return 截断后的文本
     */
    private static String truncate(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= MAX_ERROR_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_LENGTH);
    }
}
