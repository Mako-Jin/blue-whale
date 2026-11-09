package com.bluewhale.oa.drive.application.view;

import lombok.Data;
import org.springframework.core.io.Resource;

import java.time.LocalDateTime;

/**
 * 网盘业务视图集合。
 *
 * @author BlueWhale
 */
public final class DriveViews {

    /** 工具类，禁止实例化。 */
    private DriveViews() {
    }

    /**
     * 文件夹视图。
     */
    @Data
    public static class FolderView {

        private Long id;

        private Long parentId;

        private String name;

        private String scope;

        private Long deptId;

        private Long ownerId;

        private String ownerName;

        private Integer sortNo;

        private LocalDateTime createTime;

        /** 当前用户是否可写 */
        private boolean writable;

        /** 当前用户是否可管理 */
        private boolean manageable;
    }

    /**
     * 文件视图。
     */
    @Data
    public static class FileView {

        private Long id;

        private Long folderId;

        private Long fileId;

        private String fileName;

        private Long fileSize;

        private String contentType;

        private Long uploaderId;

        private String uploaderName;

        private LocalDateTime createTime;
    }

    /**
     * 授权视图。
     */
    @Data
    public static class AclView {

        private String subjectType;

        private Long subjectId;

        private String subjectName;

        private String effect;

        private String permission;
    }

    /**
     * 配额视图。
     */
    @Data
    public static class QuotaView {

        /** 已用字节数 */
        private Long usedBytes;

        /** 配额总字节数 */
        private Long totalBytes;
    }

    /**
     * 下载视图。
     */
    @Data
    public static class DownloadView {

        private Long id;

        private String fileName;

        private String contentType;

        private Long fileSize;

        /** 文件内容 */
        private Resource resource;
    }
}
