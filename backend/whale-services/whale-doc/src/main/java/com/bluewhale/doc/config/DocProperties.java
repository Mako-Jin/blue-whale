package com.bluewhale.doc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文档服务配置。
 *
 * <p>内容体积上限在应用层校验，避免超大 JSON 进入数据库与搜索投影；
 * 协作（M2）与发布审批（M3）通过开关灰度启用。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.doc")
public class DocProperties {

    /** 单页正文 JSON 最大字节数，默认 2MB */
    private long contentMaxBytes = 2L * 1024 * 1024;

    /** 单页纯文本投影最大字节数，默认 1MB */
    private long contentTextMaxBytes = 1024 * 1024;

    /** 单个附件最大字节数，默认 50MB */
    private long attachmentMaxBytes = 50L * 1024 * 1024;

    /** 页面树最大层级，默认 10 */
    private int maxDepth = 10;

    /** 是否启用实时协作（M2 默认关闭） */
    private boolean collabEnabled = false;

    /** 是否启用发布审批（M3 默认关闭） */
    private boolean approvalEnabled = false;

    /** 发布审批流程定义ID（approvalEnabled=true 时必填） */
    private Long approvalDefinitionId;

    /** 发布审批流程定义编码，用于展示与排障 */
    private String approvalDefinitionCode = "doc_publish";

    /** 发布审批轮询间隔（分钟），用于补偿流程回调缺失 */
    private int approvalSyncMinutes = 5;

    /** 是否启用站内消息通知（评论@、审批、成员变更；M3 默认关闭） */
    private boolean notifyEnabled = false;

    /** 消息渠道，逗号分隔，默认站内信 */
    private String notifyChannels = "INAPP";

    /** 是否启用归档快照入档（M3 默认关闭） */
    private boolean archiveEnabled = false;

    /** 归档业务定义编码 */
    private String archiveDefinitionCode = "DOC_PAGE";

    /** 归档快照最大字节数，默认 1MB（档案服务硬限制） */
    private long archiveSnapshotMaxBytes = 1024 * 1024;

    /** 分享默认有效期（天），0 表示不限期 */
    private int shareDefaultExpireDays = 7;

    /** 分享最大有效期（天） */
    private int shareMaxExpireDays = 365;

    /** 外部服务名：消息 */
    private String messageServiceName = "whale-message";

    /** 外部服务名：工作流 */
    private String workflowServiceName = "whale-workflow";

    /** 外部服务名：档案 */
    private String archiveServiceName = "whale-archive";

    /** 自动快照间隔（分钟），默认 10 */
    private int autoSnapshotMinutes = 10;

    /** 自动快照保留的最近 revision 数量，默认 20 */
    private int autoSnapshotRevisions = 20;

    /** 回收站保留天数，默认 30 天 */
    private int recycleRetentionDays = 30;

    /** 是否启用回收站到期自动清理 */
    private boolean recycleCleanupEnabled = true;

    /** 回收站自动清理 Cron，默认每天 03:30 */
    private String recycleCleanupCron = "0 30 3 * * *";

    /** 单轮自动清理的最大回收站子树数量 */
    private int recycleCleanupBatchSize = 200;

    /** 是否启用孤儿附件引用清理（M5 默认开启） */
    private boolean orphanCleanupEnabled = true;

    /** 孤儿附件引用清理 Cron，默认每天 04:30 */
    private String orphanCleanupCron = "0 30 4 * * *";

    /** 附件引用保留小时数，早于该阈值的无主引用才会清理，默认 24 */
    private int orphanAttachmentRetentionHours = 24;

    /** 单轮孤儿附件引用清理数量 */
    private int orphanCleanupBatchSize = 500;

    /** WS ticket 有效期（秒） */
    private long collabTicketTtlSeconds = 60;

    /** 协作更新日志保留条数 */
    private int collabUpdateKeep = 2000;

    /** 协作更新日志保留时长（秒） */
    private long collabUpdateTtlSeconds = 7200;

    /** 协作活跃标记过期时间（秒），由心跳续期 */
    private long collabActiveTtlSeconds = 180;

    /** 协作 leader 锁过期时间（秒），由快照续期 */
    private long collabLeaderTtlSeconds = 30;

    /** 单条 Yjs 更新最大字节数（解码后），默认 2MB */
    private long collabMaxUpdateBytes = 2L * 1024 * 1024;

    /** 协作快照建议间隔（秒），下发给客户端 */
    private int collabSnapshotSeconds = 12;

    /** WS 握手允许的来源，逗号分隔 */
    private String collabAllowedOrigins = "*";
}
