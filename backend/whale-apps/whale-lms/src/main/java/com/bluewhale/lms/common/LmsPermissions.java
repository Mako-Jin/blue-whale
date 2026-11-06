package com.bluewhale.lms.common;

/**
 * LMS 权限码常量（与 {@code migration-5.87-lms-m0.sql} 登记保持一致，段位 1900–1949）。
 *
 * @author BlueWhale
 */
public final class LmsPermissions {

    /** 门禁状态查询 */
    public static final String BROWSE = "sys:lms:browse";

    /** 课程列表 / 详情 */
    public static final String COURSE_LIST = "sys:lms:course:list";

    /** 新建课程 */
    public static final String COURSE_CREATE = "sys:lms:course:create";

    /** 编辑课程 */
    public static final String COURSE_UPDATE = "sys:lms:course:update";

    /** 课程发布 / 归档 */
    public static final String COURSE_STATUS = "sys:lms:course:status";

    /** 逻辑删除课程 */
    public static final String COURSE_DELETE = "sys:lms:course:delete";

    /** 章节管理 */
    public static final String CHAPTER_MANAGE = "sys:lms:chapter:manage";

    /** 课件管理 */
    public static final String WARE_MANAGE = "sys:lms:ware:manage";

    /** 我的学习列表 / 进度查询 */
    public static final String LEARNING_LIST = "sys:lms:learning:list";

    /** 报名 */
    public static final String LEARNING_ENROLL = "sys:lms:learning:enroll";

    /** 学习进度上报 */
    public static final String LEARNING_PROGRESS = "sys:lms:learning:progress";

    /** 培训班列表 / 详情 */
    public static final String TRAINING_LIST = "sys:lms:training:list";

    /** 新建培训班 */
    public static final String TRAINING_CREATE = "sys:lms:training:create";

    /** 编辑培训班 */
    public static final String TRAINING_UPDATE = "sys:lms:training:update";

    /** 逻辑删除培训班 */
    public static final String TRAINING_DELETE = "sys:lms:training:delete";

    /** 培训班发布 / 结课 */
    public static final String TRAINING_STATUS = "sys:lms:training:status";

    /** 场次维护 */
    public static final String TRAINING_SESSION = "sys:lms:training:session";

    /** 必修分配 */
    public static final String TRAINING_ASSIGN = "sys:lms:training:assign";

    /** 自助签到 */
    public static final String TRAINING_CHECKIN = "sys:lms:training:checkin";

    /** 补签与签到明细查看 */
    public static final String TRAINING_CHECKIN_MANAGE = "sys:lms:training:checkin:manage";

    /** 讲师课时台账 */
    public static final String TRAINING_HOURS = "sys:lms:training:hours";

    /** 完成认定（签到 + 课程进度） */
    public static final String TRAINING_COMPLETION = "sys:lms:training:completion";

    /** 题库列表 / 详情 */
    public static final String QUESTION_LIST = "sys:lms:question:list";

    /** 新建题目 */
    public static final String QUESTION_CREATE = "sys:lms:question:create";

    /** 编辑题目 / 启用停用 */
    public static final String QUESTION_UPDATE = "sys:lms:question:update";

    /** 删除题目 */
    public static final String QUESTION_DELETE = "sys:lms:question:delete";

    /** 试卷列表 / 详情 */
    public static final String PAPER_LIST = "sys:lms:paper:list";

    /** 新建试卷 */
    public static final String PAPER_CREATE = "sys:lms:paper:create";

    /** 编辑试卷 / 增删题目 / 规则抽题 */
    public static final String PAPER_UPDATE = "sys:lms:paper:update";

    /** 删除试卷 */
    public static final String PAPER_DELETE = "sys:lms:paper:delete";

    /** 试卷发布 / 归档 */
    public static final String PAPER_PUBLISH = "sys:lms:paper:publish";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private LmsPermissions() {
    }
}
