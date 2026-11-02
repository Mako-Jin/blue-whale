package com.bluewhale.doc.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DocProperties} 默认值与 setter 绑定断言。
 *
 * @author BlueWhale
 */
class DocPropertiesTest {

    @Test
    void shouldExposeDocumentedDefaults() {
        DocProperties properties = new DocProperties();

        assertThat(properties.getContentMaxBytes()).isEqualTo(2L * 1024 * 1024);
        assertThat(properties.getContentTextMaxBytes()).isEqualTo(1024 * 1024);
        assertThat(properties.getAttachmentMaxBytes()).isEqualTo(50L * 1024 * 1024);
        assertThat(properties.getMaxDepth()).isEqualTo(10);
        assertThat(properties.isCollabEnabled()).isFalse();
        assertThat(properties.isApprovalEnabled()).isFalse();
        assertThat(properties.getApprovalDefinitionCode()).isEqualTo("doc_publish");
        assertThat(properties.getApprovalSyncMinutes()).isEqualTo(5);
        assertThat(properties.isNotifyEnabled()).isFalse();
        assertThat(properties.getNotifyChannels()).isEqualTo("INAPP");
        assertThat(properties.isArchiveEnabled()).isFalse();
        assertThat(properties.getArchiveDefinitionCode()).isEqualTo("DOC_PAGE");
        assertThat(properties.getArchiveSnapshotMaxBytes()).isEqualTo(1024 * 1024);
        assertThat(properties.getShareDefaultExpireDays()).isEqualTo(7);
        assertThat(properties.getShareMaxExpireDays()).isEqualTo(365);
        assertThat(properties.getMessageServiceName()).isEqualTo("whale-message");
        assertThat(properties.getWorkflowServiceName()).isEqualTo("whale-workflow");
        assertThat(properties.getArchiveServiceName()).isEqualTo("whale-archive");
        assertThat(properties.getAutoSnapshotMinutes()).isEqualTo(10);
        assertThat(properties.getAutoSnapshotRevisions()).isEqualTo(20);
        assertThat(properties.getRecycleRetentionDays()).isEqualTo(30);
        assertThat(properties.isRecycleCleanupEnabled()).isTrue();
        assertThat(properties.getRecycleCleanupCron()).isEqualTo("0 30 3 * * *");
        assertThat(properties.getRecycleCleanupBatchSize()).isEqualTo(200);
        assertThat(properties.isOrphanCleanupEnabled()).isTrue();
        assertThat(properties.getOrphanCleanupCron()).isEqualTo("0 30 4 * * *");
        assertThat(properties.getOrphanAttachmentRetentionHours()).isEqualTo(24);
        assertThat(properties.getOrphanCleanupBatchSize()).isEqualTo(500);
        assertThat(properties.getCollabTicketTtlSeconds()).isEqualTo(60);
        assertThat(properties.getCollabUpdateKeep()).isEqualTo(2000);
        assertThat(properties.getCollabUpdateTtlSeconds()).isEqualTo(7200);
        assertThat(properties.getCollabActiveTtlSeconds()).isEqualTo(180);
        assertThat(properties.getCollabLeaderTtlSeconds()).isEqualTo(30);
        assertThat(properties.getCollabMaxUpdateBytes()).isEqualTo(2L * 1024 * 1024);
        assertThat(properties.getCollabSnapshotSeconds()).isEqualTo(12);
        assertThat(properties.getCollabAllowedOrigins()).isEqualTo("*");
    }

    @Test
    void shouldApplySetters() {
        DocProperties properties = new DocProperties();
        properties.setContentMaxBytes(10L);
        properties.setContentTextMaxBytes(9L);
        properties.setAttachmentMaxBytes(8L);
        properties.setMaxDepth(3);
        properties.setCollabEnabled(true);
        properties.setApprovalEnabled(true);
        properties.setApprovalDefinitionId(4L);
        properties.setApprovalDefinitionCode("code");
        properties.setNotifyChannels("INAPP,SMS");
        properties.setNotifyEnabled(true);
        properties.setArchiveEnabled(true);
        properties.setArchiveDefinitionCode("DOC");
        properties.setShareDefaultExpireDays(1);
        properties.setShareMaxExpireDays(2);
        properties.setMessageServiceName("msg");
        properties.setWorkflowServiceName("flow");
        properties.setArchiveServiceName("arc");
        properties.setAutoSnapshotMinutes(5);
        properties.setAutoSnapshotRevisions(6);
        properties.setRecycleRetentionDays(7);
        properties.setRecycleCleanupEnabled(false);
        properties.setRecycleCleanupCron("cron");
        properties.setRecycleCleanupBatchSize(11);
        properties.setOrphanCleanupEnabled(false);
        properties.setOrphanCleanupCron("cron2");
        properties.setOrphanAttachmentRetentionHours(12);
        properties.setOrphanCleanupBatchSize(13);
        properties.setCollabTicketTtlSeconds(14L);
        properties.setCollabUpdateKeep(15);
        properties.setCollabUpdateTtlSeconds(16L);
        properties.setCollabActiveTtlSeconds(17L);
        properties.setCollabLeaderTtlSeconds(18L);
        properties.setCollabMaxUpdateBytes(19L);
        properties.setCollabSnapshotSeconds(20);
        properties.setCollabAllowedOrigins("https://a.com");

        assertThat(properties.getContentMaxBytes()).isEqualTo(10L);
        assertThat(properties.getContentTextMaxBytes()).isEqualTo(9L);
        assertThat(properties.getAttachmentMaxBytes()).isEqualTo(8L);
        assertThat(properties.getMaxDepth()).isEqualTo(3);
        assertThat(properties.isCollabEnabled()).isTrue();
        assertThat(properties.isApprovalEnabled()).isTrue();
        assertThat(properties.getApprovalDefinitionId()).isEqualTo(4L);
        assertThat(properties.getApprovalDefinitionCode()).isEqualTo("code");
        assertThat(properties.getNotifyChannels()).isEqualTo("INAPP,SMS");
        assertThat(properties.isNotifyEnabled()).isTrue();
        assertThat(properties.isArchiveEnabled()).isTrue();
        assertThat(properties.getArchiveDefinitionCode()).isEqualTo("DOC");
        assertThat(properties.getShareDefaultExpireDays()).isEqualTo(1);
        assertThat(properties.getShareMaxExpireDays()).isEqualTo(2);
        assertThat(properties.getMessageServiceName()).isEqualTo("msg");
        assertThat(properties.getWorkflowServiceName()).isEqualTo("flow");
        assertThat(properties.getArchiveServiceName()).isEqualTo("arc");
        assertThat(properties.getAutoSnapshotMinutes()).isEqualTo(5);
        assertThat(properties.getAutoSnapshotRevisions()).isEqualTo(6);
        assertThat(properties.getRecycleRetentionDays()).isEqualTo(7);
        assertThat(properties.isRecycleCleanupEnabled()).isFalse();
        assertThat(properties.getRecycleCleanupCron()).isEqualTo("cron");
        assertThat(properties.getRecycleCleanupBatchSize()).isEqualTo(11);
        assertThat(properties.isOrphanCleanupEnabled()).isFalse();
        assertThat(properties.getOrphanCleanupCron()).isEqualTo("cron2");
        assertThat(properties.getOrphanAttachmentRetentionHours()).isEqualTo(12);
        assertThat(properties.getOrphanCleanupBatchSize()).isEqualTo(13);
        assertThat(properties.getCollabTicketTtlSeconds()).isEqualTo(14L);
        assertThat(properties.getCollabUpdateKeep()).isEqualTo(15);
        assertThat(properties.getCollabUpdateTtlSeconds()).isEqualTo(16L);
        assertThat(properties.getCollabActiveTtlSeconds()).isEqualTo(17L);
        assertThat(properties.getCollabLeaderTtlSeconds()).isEqualTo(18L);
        assertThat(properties.getCollabMaxUpdateBytes()).isEqualTo(19L);
        assertThat(properties.getCollabSnapshotSeconds()).isEqualTo(20);
        assertThat(properties.getCollabAllowedOrigins()).isEqualTo("https://a.com");
    }
}
