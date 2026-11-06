package com.bluewhale.lms.course.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.lms.course.domain.model.Course;
import com.bluewhale.lms.course.domain.model.CourseChapter;
import com.bluewhale.lms.course.domain.model.CourseQuery;
import com.bluewhale.lms.course.domain.model.CourseStatus;
import com.bluewhale.lms.course.domain.model.Courseware;
import com.bluewhale.lms.course.domain.model.WareType;
import com.bluewhale.lms.course.domain.repository.CourseRepository;
import com.bluewhale.lms.course.infrastructure.persistence.entity.CourseChapterEntity;
import com.bluewhale.lms.course.infrastructure.persistence.entity.CourseEntity;
import com.bluewhale.lms.course.infrastructure.persistence.entity.CoursewareEntity;
import com.bluewhale.lms.course.infrastructure.persistence.mapper.CourseChapterMapper;
import com.bluewhale.lms.course.infrastructure.persistence.mapper.CourseMapper;
import com.bluewhale.lms.course.infrastructure.persistence.mapper.CoursewareMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 课程聚合仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CourseRepositoryImpl implements CourseRepository {

    private final CourseMapper courseMapper;

    private final CourseChapterMapper chapterMapper;

    private final CoursewareMapper wareMapper;

    @Override
    public List<Course> page(CourseQuery query) {
        Page<CourseEntity> page = courseMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(CourseQuery query) {
        return courseMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Course> findById(Long id) {
        return Optional.ofNullable(courseMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Course> findByCode(String code) {
        return Optional.ofNullable(courseMapper.selectOne(new LambdaQueryWrapper<CourseEntity>()
                        .eq(CourseEntity::getCourseCode, code)))
                .map(this::toDomain);
    }

    @Override
    public Long save(Course course) {
        CourseEntity entity = toEntity(course);
        if (course.getId() == null) {
            courseMapper.insert(entity);
        } else {
            courseMapper.updateById(entity);
        }
        course.setId(entity.getId());
        course.setCreateTime(entity.getCreateTime());
        course.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public void deleteById(Long id) {
        courseMapper.deleteById(id);
    }

    @Override
    public List<CourseChapter> listChapters(Long courseId) {
        return chapterMapper.selectList(new LambdaQueryWrapper<CourseChapterEntity>()
                        .eq(CourseChapterEntity::getCourseId, courseId)
                        .orderByAsc(CourseChapterEntity::getSortNo))
                .stream().map(this::toChapterDomain).toList();
    }

    @Override
    public long countChapters(Long courseId) {
        return chapterMapper.selectCount(new LambdaQueryWrapper<CourseChapterEntity>()
                .eq(CourseChapterEntity::getCourseId, courseId));
    }

    @Override
    public Optional<CourseChapter> findChapterById(Long chapterId) {
        return Optional.ofNullable(chapterMapper.selectById(chapterId)).map(this::toChapterDomain);
    }

    @Override
    public Long saveChapter(CourseChapter chapter) {
        CourseChapterEntity entity = toChapterEntity(chapter);
        if (chapter.getId() == null) {
            chapterMapper.insert(entity);
        } else {
            chapterMapper.updateById(entity);
        }
        chapter.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public void deleteChapter(Long chapterId) {
        chapterMapper.deleteById(chapterId);
    }

    @Override
    public List<Courseware> listWares(Long courseId) {
        return wareMapper.selectList(new LambdaQueryWrapper<CoursewareEntity>()
                        .eq(CoursewareEntity::getCourseId, courseId)
                        .orderByAsc(CoursewareEntity::getSortNo))
                .stream().map(this::toWareDomain).toList();
    }

    @Override
    public List<Courseware> listWaresByChapter(Long chapterId) {
        return wareMapper.selectList(new LambdaQueryWrapper<CoursewareEntity>()
                        .eq(CoursewareEntity::getChapterId, chapterId)
                        .orderByAsc(CoursewareEntity::getSortNo))
                .stream().map(this::toWareDomain).toList();
    }

    @Override
    public Optional<Courseware> findWareById(Long wareId) {
        return Optional.ofNullable(wareMapper.selectById(wareId)).map(this::toWareDomain);
    }

    @Override
    public Long saveWare(Courseware ware) {
        CoursewareEntity entity = toWareEntity(ware);
        if (ware.getId() == null) {
            wareMapper.insert(entity);
        } else {
            wareMapper.updateById(entity);
        }
        ware.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public void deleteWare(Long wareId) {
        wareMapper.deleteById(wareId);
    }

    /**
     * 组装课程查询条件。
     *
     * @param query 查询条件
     * @return 包装器
     */
    private LambdaQueryWrapper<CourseEntity> filter(CourseQuery query) {
        LambdaQueryWrapper<CourseEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(CourseEntity::getTitle, keyword)
                    .or().like(CourseEntity::getCourseCode, keyword));
        }
        if (StringUtils.hasText(query.getCategory())) {
            wrapper.eq(CourseEntity::getCategory, query.getCategory().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(CourseEntity::getStatus, query.getStatus().trim());
        }
        wrapper.orderByDesc(CourseEntity::getCreateTime);
        return wrapper;
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 课程
     */
    private Course toDomain(CourseEntity entity) {
        Course course = new Course();
        course.setId(entity.getId());
        course.setCourseCode(entity.getCourseCode());
        course.setTitle(entity.getTitle());
        course.setCategory(entity.getCategory());
        course.setDescription(entity.getDescription());
        course.setLecturer(entity.getLecturer());
        course.setStatus(CourseStatus.of(entity.getStatus()));
        course.setCreateTime(entity.getCreateTime());
        course.setUpdateTime(entity.getUpdateTime());
        return course;
    }

    /**
     * 领域转实体。
     *
     * @param course 课程
     * @return 实体
     */
    private CourseEntity toEntity(Course course) {
        CourseEntity entity = new CourseEntity();
        entity.setId(course.getId());
        entity.setCourseCode(course.getCourseCode());
        entity.setTitle(course.getTitle());
        entity.setCategory(course.getCategory());
        entity.setDescription(course.getDescription());
        entity.setLecturer(course.getLecturer());
        entity.setStatus(course.getStatus().name());
        entity.setCreateTime(course.getCreateTime());
        entity.setUpdateTime(course.getUpdateTime());
        return entity;
    }

    /**
     * 章节实体转领域。
     *
     * @param entity 实体
     * @return 章节
     */
    private CourseChapter toChapterDomain(CourseChapterEntity entity) {
        CourseChapter chapter = new CourseChapter();
        chapter.setId(entity.getId());
        chapter.setCourseId(entity.getCourseId());
        chapter.setTitle(entity.getTitle());
        chapter.setSortNo(entity.getSortNo());
        chapter.setRemark(entity.getRemark());
        return chapter;
    }

    /**
     * 章节领域转实体。
     *
     * @param chapter 章节
     * @return 实体
     */
    private CourseChapterEntity toChapterEntity(CourseChapter chapter) {
        CourseChapterEntity entity = new CourseChapterEntity();
        entity.setId(chapter.getId());
        entity.setCourseId(chapter.getCourseId());
        entity.setTitle(chapter.getTitle());
        entity.setSortNo(chapter.getSortNo());
        entity.setRemark(chapter.getRemark());
        return entity;
    }

    /**
     * 课件实体转领域。
     *
     * @param entity 实体
     * @return 课件
     */
    private Courseware toWareDomain(CoursewareEntity entity) {
        Courseware ware = new Courseware();
        ware.setId(entity.getId());
        ware.setCourseId(entity.getCourseId());
        ware.setChapterId(entity.getChapterId());
        ware.setName(entity.getName());
        ware.setWareType(WareType.of(entity.getWareType()));
        ware.setFileRefId(entity.getFileRefId());
        ware.setLinkUrl(entity.getLinkUrl());
        ware.setSortNo(entity.getSortNo());
        return ware;
    }

    /**
     * 课件领域转实体。
     *
     * @param ware 课件
     * @return 实体
     */
    private CoursewareEntity toWareEntity(Courseware ware) {
        CoursewareEntity entity = new CoursewareEntity();
        entity.setId(ware.getId());
        entity.setCourseId(ware.getCourseId());
        entity.setChapterId(ware.getChapterId());
        entity.setName(ware.getName());
        entity.setWareType(ware.getWareType().name());
        entity.setFileRefId(ware.getFileRefId());
        entity.setLinkUrl(ware.getLinkUrl());
        entity.setSortNo(ware.getSortNo());
        return entity;
    }
}
