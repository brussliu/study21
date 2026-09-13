package com.study21.user.todo;

import java.sql.Timestamp;
import java.time.LocalDate;

/** COM_TODO情報 の 1 行（Mapper の戻り値）。 */
public class TodoEntity {

    private Long todoId;
    private Long accountId;
    private Long createdByAccountId;
    private Long parentTodoId;
    private Integer order;
    private String title;
    private String memo;
    private String status;
    private String priority;
    private LocalDate dueDate;
    private Timestamp startedAt;
    private Timestamp completedAt;
    private Integer version;

    public Long getTodoId() { return todoId; }
    public void setTodoId(Long todoId) { this.todoId = todoId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getCreatedByAccountId() { return createdByAccountId; }
    public void setCreatedByAccountId(Long createdByAccountId) { this.createdByAccountId = createdByAccountId; }
    public Long getParentTodoId() { return parentTodoId; }
    public void setParentTodoId(Long parentTodoId) { this.parentTodoId = parentTodoId; }
    public Integer getOrder() { return order; }
    public void setOrder(Integer order) { this.order = order; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Timestamp getStartedAt() { return startedAt; }
    public void setStartedAt(Timestamp startedAt) { this.startedAt = startedAt; }
    public Timestamp getCompletedAt() { return completedAt; }
    public void setCompletedAt(Timestamp completedAt) { this.completedAt = completedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
