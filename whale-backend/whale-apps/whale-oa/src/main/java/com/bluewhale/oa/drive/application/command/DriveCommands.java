package com.bluewhale.oa.drive.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 网盘业务命令集合。
 *
 * @author BlueWhale
 */
public final class DriveCommands {

    /** 工具类，禁止实例化。 */
    private DriveCommands() {
    }

    /**
     * 新建文件夹命令。
     */
    @Data
    public static class FolderCreateCommand {

        /** 父文件夹ID（0 或空表示根） */
        private Long parentId;

        /** 文件夹名称 */
        @NotBlank(message = "{validation.oa.folder.name-required}")
        @Size(max = 64, message = "{validation.oa.folder.name-size}")
        private String name;

        /** 范围：PERSONAL / DEPARTMENT */
        private String scope;

        /** 部门ID（部门文件夹必填） */
        private Long deptId;

        /** 排序号 */
        private Integer sortNo;
    }

    /**
     * 修改文件夹命令（仅名称与排序）。
     */
    @Data
    public static class FolderUpdateCommand {

        /** 文件夹名称 */
        @NotBlank(message = "{validation.oa.folder.name-required}")
        @Size(max = 64, message = "{validation.oa.folder.name-size}")
        private String name;

        /** 排序号 */
        private Integer sortNo;
    }

    /**
     * 文件登记命令。
     */
    @Data
    public static class FileRegisterCommand {

        /** 文件夹ID */
        @NotNull(message = "{validation.oa.file-register.folder-id-required}")
        private Long folderId;

        /** whale-file 文件ID */
        @NotNull(message = "{validation.oa.file-register.file-id-required}")
        private Long fileId;
    }

    /**
     * 文件夹授权整体替换命令。
     */
    @Data
    public static class AclSaveCommand {

        /** 授权条目 */
        private List<AclItemCommand> acls;
    }

    /**
     * 授权条目。
     */
    @Data
    public static class AclItemCommand {

        /** 主体类型：USER / DEPT */
        @NotBlank(message = "{validation.oa.acl-item.subject-type-required}")
        private String subjectType;

        /** 主体ID */
        @NotNull(message = "{validation.oa.acl-item.subject-id-required}")
        private Long subjectId;

        /** 主体名称快照 */
        private String subjectName;

        /** 效果：ALLOW / DENY */
        @NotBlank(message = "{validation.oa.acl-item.effect-required}")
        private String effect;

        /** 权限：READ / WRITE / MANAGE */
        @NotBlank(message = "{validation.oa.acl-item.permission-required}")
        private String permission;
    }
}
