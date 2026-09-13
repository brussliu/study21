package com.study21.user.todo;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** COM_TODO情報 の Mapper。 */
@Mapper
public interface TodoMapper {

    long count(@Param("accountId") long accountId,
               @Param("keyword") String keyword,
               @Param("status") String status,
               @Param("priority") String priority,
               @Param("includeDone") boolean includeDone,
               @Param("dueFrom") java.time.LocalDate dueFrom,
               @Param("dueTo") java.time.LocalDate dueTo);

    List<TodoEntity> search(@Param("accountId") long accountId,
                            @Param("keyword") String keyword,
                            @Param("status") String status,
                            @Param("priority") String priority,
                            @Param("includeDone") boolean includeDone,
                            @Param("dueFrom") java.time.LocalDate dueFrom,
                            @Param("dueTo") java.time.LocalDate dueTo,
                            @Param("limit") int limit,
                            @Param("offset") int offset);

    List<TodoEntity> findChildren(@Param("parentIds") List<Long> parentIds);

    TodoEntity findById(@Param("todoId") long todoId);

    long countByStatus(@Param("accountId") long accountId, @Param("status") String status);

    List<TodoModels.CalendarCell> countByDueDate(@Param("accountId") long accountId,
                                                 @Param("from") java.time.LocalDate from,
                                                 @Param("to") java.time.LocalDate to);

    int insert(TodoEntity entity);

    int update(@Param("todoId") long todoId,
               @Param("title") String title,
               @Param("memo") String memo,
               @Param("priority") String priority,
               @Param("dueDate") java.time.LocalDate dueDate,
               @Param("version") Integer version,
               @Param("updatedByAccountId") Long updatedByAccountId);

    /** 状態を変える（開始・完了・差し戻し）。時刻は状態に応じてサービス側で入れる。 */
    int updateStatus(@Param("todoId") long todoId,
                     @Param("status") String status,
                     @Param("startedAt") java.sql.Timestamp startedAt,
                     @Param("completedAt") java.sql.Timestamp completedAt,
                     @Param("version") Integer version,
                     @Param("updatedByAccountId") Long updatedByAccountId);

    int delete(@Param("todoId") long todoId);

    int deleteChildren(@Param("parentTodoId") long parentTodoId);
}
