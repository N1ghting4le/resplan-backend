package com.resplan.repository;

import com.resplan.error.NotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Общий предок репозиториев (принцип DRY): поиск сущности по идентификатору
 * с единообразной ошибкой 404 вместо повторения orElseThrow в каждом сервисе.
 */
@NoRepositoryBean
public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {

    default T require(ID id, String entityName) {
        return findById(id).orElseThrow(() -> new NotFoundException(entityName, id));
    }
}
