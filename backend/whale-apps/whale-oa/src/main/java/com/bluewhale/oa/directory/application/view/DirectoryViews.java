package com.bluewhale.oa.directory.application.view;

import lombok.Data;

import java.util.List;

/**
 * 通讯录业务视图集合。
 *
 * @author BlueWhale
 */
public final class DirectoryViews {

    private DirectoryViews() {
    }

    /**
     * 通讯录用户视图。
     */
    @Data
    public static class UserView {

        private Long id;

        private String username;

        private String nickname;

        private Long deptId;

        private String deptName;

        private String phone;

        private String email;
    }

    /**
     * 部门树视图。
     */
    @Data
    public static class DeptView {

        private Long id;

        private Long parentId;

        private String deptName;

        private List<DeptView> children;
    }
}
