package com.resplan.repository;

import com.resplan.domain.Task;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TaskRepository extends BaseRepository<Task, Integer> {

    @Query("select distinct t from Task t left join fetch t.predecessors where t.project.id = :projectId order by t.id")
    List<Task> findByProjectWithPredecessors(Integer projectId);

    /** Задачи-последователи, у которых task указана предшественником */
    @Query("select t from Task t join t.predecessors p where p = :task")
    List<Task> findSuccessors(Task task);
}
