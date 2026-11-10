package com.bluewhale.lms.integration.application.view;

import lombok.Data;

/**
 * 单个培训班的催办结果。
 *
 * <p>{@code skippedOffboard} 与 {@code skippedUnrecorded} 分开计数，因为处置方式不同：
 * 前者是正常业务结果（人已离职，本就不该再发），后者是待办事项（快照尚未收录
 * 该账号，需要先跑一次员工目录同步）。混成一个「跳过数」就无法区分。</p>
 *
 * @author BlueWhale
 */
@Data
public class UrgeItemView {

    /** 培训班 ID */
    private Long classId;

    /** 培训班编码 */
    private String classCode;

    /** 培训班名称 */
    private String className;

    /** 未完成人数 */
    private int pendingCount;

    /** 实际投递人数 */
    private int notified;

    /** 因离职跳过人数 */
    private int skippedOffboard;

    /** 因快照未收录跳过人数 */
    private int skippedUnrecorded;

    /** 失败原因（有异常时非空） */
    private String error;
}
