package com.bluewhale.lms.course.domain.repository;

import com.bluewhale.lms.course.domain.model.Course;
import com.bluewhale.lms.course.domain.model.CourseChapter;
import com.bluewhale.lms.course.domain.model.CourseQuery;
import com.bluewhale.lms.course.domain.model.Courseware;

import java.util.List;
import java.util.Optional;

/**
 * 课程聚合仓储（含章节与课件实体）。
 *
 * @author BlueWhale
 */
public interface CourseRepository {

    /**
     * 分页查询课程。
     *
     * @param query 查询条件
     * @return 课程列表
     */
    List<Course> page(CourseQuery query);

    /**
     * 统计课程总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(CourseQuery query);

    /**
     * 按 ID 查询。
     *
     * @param id 课程 ID
     * @return 课程
     */
    Optional<Course> findById(Long id);

    /**
     * 按编码查询。
     *
     * @param code 课程编码
     * @return 课程
     */
    Optional<Course> findByCode(String code);

    /**
     * 保存课程（ID 为空插入，否则更新）。
     *
     * @param course 课程
     * @return 主键
     */
    Long save(Course course);

    /**
     * 逻辑删除课程。
     *
     * @param id 课程 ID
     */
    void deleteById(Long id);

    /**
     * 查询课程全部章节（按 sortNo 升序）。
     *
     * @param courseId 课程 ID
     * @return 章节列表
     */
    List<CourseChapter> listChapters(Long courseId);

    /**
     * 统计课程章节数。
     *
     * @param courseId 课程 ID
     * @return 章节数
     */
    long countChapters(Long courseId);

    /**
     * 按 ID 查询章节。
     *
     * @param chapterId 章节 ID
     * @return 章节
     */
    Optional<CourseChapter> findChapterById(Long chapterId);

    /**
     * 保存章节。
     *
     * @param chapter 章节
     * @return 主键
     */
    Long saveChapter(CourseChapter chapter);

    /**
     * 逻辑删除章节（连同其课件）。
     *
     * @param chapterId 章节 ID
     */
    void deleteChapter(Long chapterId);

    /**
     * 查询课程全部课件。
     *
     * @param courseId 课程 ID
     * @return 课件列表
     */
    List<Courseware> listWares(Long courseId);

    /**
     * 按章节查询课件。
     *
     * @param chapterId 章节 ID
     * @return 课件列表
     */
    List<Courseware> listWaresByChapter(Long chapterId);

    /**
     * 按 ID 查询课件。
     *
     * @param wareId 课件 ID
     * @return 课件
     */
    Optional<Courseware> findWareById(Long wareId);

    /**
     * 保存课件。
     *
     * @param ware 课件
     * @return 主键
     */
    Long saveWare(Courseware ware);

    /**
     * 逻辑删除课件。
     *
     * @param wareId 课件 ID
     */
    void deleteWare(Long wareId);
}
