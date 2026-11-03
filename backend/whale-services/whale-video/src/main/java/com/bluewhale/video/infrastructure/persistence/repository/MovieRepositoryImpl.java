package com.bluewhale.video.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.video.domain.model.Movie;
import com.bluewhale.video.domain.model.MovieLicenseSource;
import com.bluewhale.video.domain.repository.MovieRepository;
import com.bluewhale.video.infrastructure.persistence.entity.MovieEntity;
import com.bluewhale.video.infrastructure.persistence.mapper.MovieMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 电影元数据仓储实现（MyBatis-Plus）。
 *
 * <p>更新时显式覆盖所有可空列（授权编号 / 上映日期 / 评分 / 海报 / 背景图），
 * 以便运营清空此前填写的可选信息。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class MovieRepositoryImpl implements MovieRepository {

    private final MovieMapper movieMapper;

    @Override
    public Optional<Movie> findByContentId(Long contentId) {
        return Optional.ofNullable(movieMapper.selectById(contentId)).map(this::toDomain);
    }

    @Override
    public Map<Long, Movie> findByContentIds(Collection<Long> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return movieMapper.selectBatchIds(contentIds).stream()
                .map(this::toDomain)
                .collect(Collectors.toMap(Movie::getContentId, movie -> movie, (left, right) -> left));
    }

    @Override
    public void insert(Movie movie) {
        MovieEntity entity = new MovieEntity();
        entity.setContentId(movie.getContentId());
        entity.setCreateTime(movie.getCreateTime() == null ? LocalDateTime.now() : movie.getCreateTime());
        entity.setOriginalTitle(movie.getOriginalTitle());
        entity.setDirector(movie.getDirector());
        entity.setActors(movie.getActors());
        entity.setRegion(movie.getRegion());
        entity.setLanguage(movie.getLanguage());
        entity.setReleaseDate(movie.getReleaseDate());
        entity.setRating(movie.getRating());
        entity.setLicenseSource(nameOf(movie.getLicenseSource()));
        entity.setLicenseNo(movie.getLicenseNo());
        entity.setLicenseExpireAt(movie.getLicenseExpireAt());
        entity.setPosterFileId(movie.getPosterFileId());
        entity.setBackdropFileId(movie.getBackdropFileId());
        movieMapper.insert(entity);
        movie.setCreateTime(entity.getCreateTime());
    }

    @Override
    public void update(Movie movie) {
        movieMapper.update(null, new LambdaUpdateWrapper<MovieEntity>()
                .eq(MovieEntity::getContentId, movie.getContentId())
                .set(MovieEntity::getOriginalTitle, movie.getOriginalTitle())
                .set(MovieEntity::getDirector, movie.getDirector())
                .set(MovieEntity::getActors, movie.getActors())
                .set(MovieEntity::getRegion, movie.getRegion())
                .set(MovieEntity::getLanguage, movie.getLanguage())
                .set(MovieEntity::getReleaseDate, movie.getReleaseDate())
                .set(MovieEntity::getRating, movie.getRating())
                .set(MovieEntity::getLicenseSource, nameOf(movie.getLicenseSource()))
                .set(MovieEntity::getLicenseNo, movie.getLicenseNo())
                .set(MovieEntity::getLicenseExpireAt, movie.getLicenseExpireAt())
                .set(MovieEntity::getPosterFileId, movie.getPosterFileId())
                .set(MovieEntity::getBackdropFileId, movie.getBackdropFileId()));
    }

    @Override
    public void delete(Long contentId) {
        movieMapper.deleteById(contentId);
    }

    @Override
    public List<Long> listExpiredLicenseContentIds(LocalDateTime now, int limit) {
        List<Long> ids = movieMapper.selectExpiredLicenseContentIds(now, Math.max(limit, 1));
        return ids == null ? Collections.emptyList() : ids;
    }

    @Override
    public long countLicenseExpiring(LocalDateTime now, int days) {
        return movieMapper.countLicenseExpiring(now.plusDays(Math.max(days, 0)));
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 电影元数据
     */
    private Movie toDomain(MovieEntity entity) {
        Movie movie = new Movie();
        movie.setContentId(entity.getContentId());
        movie.setOriginalTitle(entity.getOriginalTitle());
        movie.setDirector(entity.getDirector());
        movie.setActors(entity.getActors());
        movie.setRegion(entity.getRegion());
        movie.setLanguage(entity.getLanguage());
        movie.setReleaseDate(entity.getReleaseDate());
        movie.setRating(entity.getRating());
        movie.setLicenseSource(MovieLicenseSource.parse(entity.getLicenseSource()));
        movie.setLicenseNo(entity.getLicenseNo());
        movie.setLicenseExpireAt(entity.getLicenseExpireAt());
        movie.setPosterFileId(entity.getPosterFileId());
        movie.setBackdropFileId(entity.getBackdropFileId());
        movie.setCreateTime(entity.getCreateTime());
        return movie;
    }

    /**
     * 取枚举名称用于存储。
     *
     * @param value 枚举值
     * @return 枚举名称；为空返回 {@code null}
     */
    private String nameOf(MovieLicenseSource value) {
        return value == null ? null : value.name();
    }
}
