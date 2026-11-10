package com.bluewhale.twin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.twin.dto.TwinAlertDispatchVO;
import com.bluewhale.twin.entity.TwinAlertDispatch;
import com.bluewhale.twin.mapper.TwinAlertDispatchMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

    /** 列表默认条数 */
    private static final int LIST_DEFAULT_LIMIT = 50;

    /** 列表条数上限 */
    private static final int LIST_MAX_LIMIT = 200;

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final TwinAlertDispatchMapper dispatchMapper;

    /**
     * 场景维度投递记录列表（Wave 6.10 M2）：按时间倒序，可按结果/通道过滤。
     *
     * @param sceneId 场景ID
     * @param status  结果过滤（SUCCESS / FAILED / SKIPPED，可空 = 全部）
     * @param channel 通道过滤（WEBHOOK / INAPP，可空 = 全部）
     * @param limit   条数上限（默认 50，最大 200）
     * @return 投递记录列表
     */
    public List<TwinAlertDispatchVO> list(Long sceneId, String status, String channel, Integer limit) {
        int capped = limit == null ? LIST_DEFAULT_LIMIT
                : Math.min(LIST_MAX_LIMIT, Math.max(1, limit));
        LambdaQueryWrapper<TwinAlertDispatch> query = new LambdaQueryWrapper<TwinAlertDispatch>()
                .eq(TwinAlertDispatch::getSceneId, sceneId)
                .orderByDesc(TwinAlertDispatch::getCreateTime)
                .orderByDesc(TwinAlertDispatch::getId)
                .last("LIMIT " + capped);
        if (StringUtils.hasText(status)) {
            query.eq(TwinAlertDispatch::getStatus, status.trim().toUpperCase());
        }
        if (StringUtils.hasText(channel)) {
            query.eq(TwinAlertDispatch::getChannel, channel.trim().toUpperCase());
        }
        List<TwinAlertDispatch> rows = dispatchMapper.selectList(query);
        List<TwinAlertDispatchVO> result = new ArrayList<>(rows.size());
        for (TwinAlertDispatch row : rows) {
            result.add(toVo(row));
        }
        return result;
    }

    /**
     * 实体转视图（ID 字符串化，时间 ISO-8601，避免前端精度丢失）。
     *
     * @param row 投递记录
     * @return 视图
     */
    private TwinAlertDispatchVO toVo(TwinAlertDispatch row) {
        return TwinAlertDispatchVO.builder()
                .id(row.getId() == null ? null : row.getId().toString())
                .alertId(row.getAlertId() == null ? null : row.getAlertId().toString())
                .sceneId(row.getSceneId() == null ? null : row.getSceneId().toString())
                .stateKey(row.getStateKey())
                .level(row.getLevel())
                .channel(row.getChannel())
                .target(row.getTarget())
                .status(row.getStatus())
                .messageId(row.getMessageId() == null ? null : row.getMessageId().toString())
                .errorMsg(row.getErrorMsg())
                .createTime(row.getCreateTime() == null ? null : TIME_FORMAT.format(row.getCreateTime()))
                .build();
    }

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
