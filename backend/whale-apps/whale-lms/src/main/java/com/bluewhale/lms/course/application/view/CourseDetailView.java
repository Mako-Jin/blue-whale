package com.bluewhale.lms.course.application.view;

import com.bluewhale.lms.course.domain.model.Course;
import com.bluewhale.lms.course.domain.model.CourseChapter;
import com.bluewhale.lms.course.domain.model.Courseware;
import lombok.Data;

import java.util.List;

/**
 * 课程详情视图（含章节与课件树）。
 *
 * @author BlueWhale
 */
@Data
public class CourseDetailView {

    /** 课程 */
    private CourseView course;

    /** 章节列表（含课件） */
    private List<ChapterView> chapters;

    /** 章节数 */
    private long chapterCount;

    /**
     * 从领域模型转换。
     *
     * @param course 课程
     * @param chapters 章节
     * @param wares 课件
     * @return 视图
     */
    public static CourseDetailView from(Course course, List<CourseChapter> chapters, List<Courseware> wares) {
        CourseDetailView detail = new CourseDetailView();
        detail.setCourse(CourseView.from(course));
        detail.setChapters(chapters.stream()
                .map(chapter -> ChapterView.from(chapter, wares.stream()
                        .filter(ware -> ware.getChapterId().equals(chapter.getId()))
                        .toList()))
                .toList());
        detail.setChapterCount(chapters.size());
        return detail;
    }

    /**
     * 章节视图。
     *
     * @author BlueWhale
     */
    @Data
    public static class ChapterView {

        /** 主键 */
        private Long id;

        /** 章节标题 */
        private String title;

        /** 排序号 */
        private Integer sortNo;

        /** 备注 */
        private String remark;

        /** 课件列表 */
        private List<WareView> wares;

        /**
         * 从领域模型转换。
         *
         * @param chapter 章节
         * @param wares 该章节课件
         * @return 视图
         */
        public static ChapterView from(CourseChapter chapter, List<Courseware> wares) {
            ChapterView view = new ChapterView();
            view.setId(chapter.getId());
            view.setTitle(chapter.getTitle());
            view.setSortNo(chapter.getSortNo());
            view.setRemark(chapter.getRemark());
            view.setWares(wares.stream().map(WareView::from).toList());
            return view;
        }
    }

    /**
     * 课件视图。
     *
     * @author BlueWhale
     */
    @Data
    public static class WareView {

        /** 主键 */
        private Long id;

        /** 课件名称 */
        private String name;

        /** 类型 */
        private String wareType;

        /** whale-file 文件引用 ID */
        private String fileRefId;

        /** 外部链接 */
        private String linkUrl;

        /** 排序号 */
        private Integer sortNo;

        /**
         * 从领域模型转换。
         *
         * @param ware 课件
         * @return 视图
         */
        public static WareView from(Courseware ware) {
            WareView view = new WareView();
            view.setId(ware.getId());
            view.setName(ware.getName());
            view.setWareType(ware.getWareType().name());
            view.setFileRefId(ware.getFileRefId());
            view.setLinkUrl(ware.getLinkUrl());
            view.setSortNo(ware.getSortNo());
            return view;
        }
    }
}
