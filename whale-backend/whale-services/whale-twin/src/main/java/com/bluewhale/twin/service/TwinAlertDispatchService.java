package com.bluewhale.twin.service;

import com.bluewhale.twin.entity.TwinAlertDispatch;
import com.bluewhale.twin.mapper.TwinAlertDispatchMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 告警外发投递记录服务（Wave 6.10 M1）：每次投递尝试落一行。
 *
 * <p>投递记录是旁路：写入失败只记 WARN，绝不影响告警落库、页面可见性与后续通道。
 * 写入时机在外发线程内，此时没有请求上下文，租户ID由调用方显式带入。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwinAlertDispatchService {

    /** 通道：外部 webhook */
    public static final String CHANNEL_WEBHOOK = "WEBHOOK";

    /** 通道：站内信（whale-message） */
    public static final String CHANNEL_INAPP = "INAPP";

    /** 结果：投递成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /** 结果：投递失败 */
    public static final String STATUS_FAILED = "FAILED";

    /** 结果：前置条件不满足（无接收人等），未发起投递 */
    public static final String STATUS_SKIPPED = "SKIPPED";

    /** 失败原因列宽 */
    private static final int ERROR_MAX_LENGTH = 512;

    /** 目标列宽 */
    private static final int TARGET_MAX_LENGTH = 256;

    private final TwinAlertDispatchMapper dispatchMapper;

    /**
     * 落一条投递记录。
     *
     * @param dispatch 投递记录（字段由调用方按实际投递组装）
     */
    public void record(TwinAlertDispatch dispatch) {
        if (dispatch == null) {
            return;
        }
        try {
            dispatch.setTarget(truncate(dispatch.getTarget(), TARGET_MAX_LENGTH));
            dispatch.setErrorMsg(truncate(dispatch.getErrorMsg(), ERROR_MAX_LENGTH));
            dispatchMapper.insert(dispatch);
        } catch (RuntimeException error) {
            log.warn("告警投递记录写入失败（忽略）：sceneId={}, channel={}, reason={}",
                    dispatch.getSceneId(), dispatch.getChannel(), error.getMessage());
        }
    }

    /**
     * 按列宽截断文本。
     *
     * @param text      原始文本（可空）
     * @param maxLength 最大长度
     * @return 截断后的文本；原始为空时返回 {@code null}
     */
    private String truncate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
