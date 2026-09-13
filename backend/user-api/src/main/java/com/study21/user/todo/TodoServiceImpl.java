package com.study21.user.todo;

import com.study21.user.account.AccountEntity;
import com.study21.user.account.AccountMapper;
import com.study21.user.security.UserPrincipal;
import com.study21.common.core.exception.ConflictException;
import com.study21.common.security.exception.ForbiddenException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * TODO の業務処理の実装。
 */
@Service
public class TodoServiceImpl implements TodoService {

    private static final int MAX_SIZE = 100;
    private static final int DEFAULT_SIZE = 20;

    private final TodoMapper todoMapper;
    private final AccountMapper accountMapper;

    public TodoServiceImpl(TodoMapper todoMapper, AccountMapper accountMapper) {
        this.todoMapper = todoMapper;
        this.accountMapper = accountMapper;
    }

    @Override
    public TodoModels.TodoListResult search(UserPrincipal user, String keyword, String status, String priority,
                                            boolean includeDone, LocalDate dueFrom, LocalDate dueTo,
                                            int page, int size) {
        String statusCode = code(status, TodoModels.STATUSES, "状態");
        String priorityCode = code(priority, TodoModels.PRIORITIES, "優先度");
        String keywordFilter = blankToNull(keyword);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int safePage = Math.max(1, page);

        long total = todoMapper.count(user.accountId(), keywordFilter, statusCode, priorityCode,
                includeDone, dueFrom, dueTo);
        List<TodoEntity> parents = todoMapper.search(user.accountId(), keywordFilter, statusCode, priorityCode,
                includeDone, dueFrom, dueTo, safeSize, (safePage - 1) * safeSize);

        List<TodoModels.TodoRow> items = new ArrayList<>();
        if (!parents.isEmpty()) {
            List<Long> parentIds = parents.stream().map(TodoEntity::getTodoId).toList();
            List<TodoEntity> children = todoMapper.findChildren(parentIds);
            for (TodoEntity parent : parents) {
                List<TodoModels.TodoRow> childRows = children.stream()
                        .filter(child -> parent.getTodoId().equals(child.getParentTodoId()))
                        .map(child -> toRow(child, List.of()))
                        .toList();
                items.add(toRow(parent, childRows));
            }
        }

        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new TodoModels.TodoListResult(items, total, safePage, safeSize, totalPages,
                todoMapper.countByStatus(user.accountId(), "TODO"),
                todoMapper.countByStatus(user.accountId(), "DOING"),
                todoMapper.countByStatus(user.accountId(), "DONE"));
    }

    @Override
    public TodoModels.CalendarResult calendar(UserPrincipal user, YearMonth month) {
        if (month == null) {
            throw new ValidationException("年月を指定してください。");
        }
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        return new TodoModels.CalendarResult(month.getYear(), month.getMonthValue(),
                todoMapper.countByDueDate(user.accountId(), from, to));
    }

    @Override
    @Transactional
    public TodoModels.TodoMutationResult create(UserPrincipal user, TodoModels.TodoSaveRequest request) {
        if (request == null || blankToNull(request.title()) == null) {
            throw new ValidationException("タイトルを入力してください。");
        }
        String priority = normalizePriority(request.priority());
        List<TodoModels.ChildRequest> children = childRows(request.children());

        TodoEntity parent = newParent(user.accountId(), user.accountId(), request.title().trim(),
                blankToNull(request.memo()), priority, request.dueDate(), 0);
        todoMapper.insert(parent);
        insertChildren(parent.getTodoId(), user.accountId(), user.accountId(), children);

        // 保護者で「お子さまにも登録する」を選んだ場合は、同じ内容を子どもにも作る
        Long studentId = resolveStudentOwnerId(user, request.alsoForStudent());
        if (studentId != null) {
            TodoEntity copy = newParent(studentId, user.accountId(), request.title().trim(),
                    blankToNull(request.memo()), priority, request.dueDate(), 0);
            todoMapper.insert(copy);
            insertChildren(copy.getTodoId(), studentId, user.accountId(), children);
        }

        return new TodoModels.TodoMutationResult("TODOを登録しました。", parent.getTodoId(),
                studentId == null ? 1 : 2);
    }

    @Override
    @Transactional
    public TodoModels.TodoMutationResult update(UserPrincipal user, long todoId, TodoModels.TodoSaveRequest request) {
        TodoEntity current = requireOwned(user, todoId);
        if (blankToNull(request.title()) == null) {
            throw new ValidationException("タイトルを入力してください。");
        }
        int updated = todoMapper.update(todoId, request.title().trim(), blankToNull(request.memo()),
                normalizePriority(request.priority()), request.dueDate(), request.version(), user.accountId());
        if (updated == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        // 子タスクは「送られた内容で入れ替える」（画面は Excel 風グリッド全体を送る）
        todoMapper.deleteChildren(todoId);
        insertChildren(todoId, user.accountId(), user.accountId(), childRows(request.children()));
        return new TodoModels.TodoMutationResult("TODOを更新しました。", current.getTodoId(), 1);
    }

    @Override
    @Transactional
    public TodoModels.TodoMutationResult delete(UserPrincipal user, long todoId) {
        requireOwned(user, todoId);
        todoMapper.delete(todoId);
        return new TodoModels.TodoMutationResult("TODOを削除しました。", todoId, 1);
    }

    @Override
    @Transactional
    public TodoModels.TodoMutationResult updateStatus(UserPrincipal user, long todoId, String status, Integer version) {
        requireOwned(user, todoId);
        String statusCode = code(status, TodoModels.STATUSES, "状態");
        if (statusCode == null) {
            throw new ValidationException("状態を指定してください。");
        }
        Timestamp startedAt = null;
        Timestamp completedAt = null;
        if (!"TODO".equals(statusCode)) {
            startedAt = Timestamp.valueOf(LocalDateTime.now());
        }
        if ("DONE".equals(statusCode)) {
            completedAt = Timestamp.valueOf(LocalDateTime.now());
        }
        int updated = todoMapper.updateStatus(todoId, statusCode, startedAt, completedAt, version, user.accountId());
        if (updated == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        // 「完了」にしたときは子タスクも完了にする（親が終わったら中身も終わっているのが自然）
        if ("DONE".equals(statusCode)) {
            for (TodoEntity child : todoMapper.findChildren(List.of(todoId))) {
                if (!"DONE".equals(child.getStatus())) {
                    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                    todoMapper.updateStatus(child.getTodoId(), "DONE", now, now, null, user.accountId());
                }
            }
        }
        String message = switch (statusCode) {
            case "DOING" -> "TODOを進行中にしました。";
            case "DONE" -> "TODOを完了しました。";
            default -> "TODOを未着手に戻しました。";
        };
        return new TodoModels.TodoMutationResult(message, todoId, 1);
    }

    /** 持ち主本人の TODO か確かめる（他人の TODO は 404 にする）。 */
    private TodoEntity requireOwned(UserPrincipal user, long todoId) {
        TodoEntity entity = todoMapper.findById(todoId);
        if (entity == null || !Long.valueOf(user.accountId()).equals(entity.getAccountId())) {
            throw new NotFoundException("TODOが見つかりません。");
        }
        return entity;
    }

    /**
     * 保護者が「お子さまの TODO にも登録する」を選んだときの登録先。
     * 選んでいなければ null。保護者以外や、お子さまが紐づいていない場合は 400。
     */
    private Long resolveStudentOwnerId(UserPrincipal user, Boolean alsoForStudent) {
        if (!Boolean.TRUE.equals(alsoForStudent)) {
            return null;
        }
        if (!"GUARDIAN".equals(String.valueOf(user.accountType()))) {
            throw new ForbiddenException("この操作は保護者アカウントでのみ利用できます。");
        }
        AccountEntity student = accountMapper.findStudentByGuardianId(user.accountId());
        if (student == null) {
            throw new ValidationException("お子さまのアカウントが見つからないため、お子さまには登録できません。");
        }
        return student.getAccountId();
    }

    private TodoEntity newParent(long ownerId, long createdBy, String title, String memo,
                                 String priority, LocalDate dueDate, int order) {
        TodoEntity entity = new TodoEntity();
        entity.setAccountId(ownerId);
        entity.setCreatedByAccountId(createdBy);
        entity.setParentTodoId(null);
        entity.setOrder(order);
        entity.setTitle(title);
        entity.setMemo(memo);
        entity.setStatus("TODO");
        entity.setPriority(priority);
        entity.setDueDate(dueDate);
        return entity;
    }

    /** Excel 風グリッドの行を子タスクとして入れる（タイトルが空の行は無視する）。 */
    private void insertChildren(long parentTodoId, long ownerId, long createdBy,
                                List<TodoModels.ChildRequest> children) {
        int order = 0;
        for (TodoModels.ChildRequest child : children) {
            if (blankToNull(child.title()) == null) {
                continue;
            }
            // 持ち主は親タスクと同じ（子タスクも同じ人のもの）
            TodoEntity entity = newParent(ownerId, createdBy, child.title().trim(), blankToNull(child.memo()),
                    normalizePriority(child.priority()), child.dueDate(), order);
            entity.setParentTodoId(parentTodoId);
            todoMapper.insert(entity);
            order += 1;
        }
    }

    private List<TodoModels.ChildRequest> childRows(List<TodoModels.ChildRequest> children) {
        return children == null ? List.of() : children;
    }

    private TodoModels.TodoRow toRow(TodoEntity entity, List<TodoModels.TodoRow> children) {
        int doneChildren = (int) children.stream().filter(child -> "DONE".equals(child.status())).count();
        return new TodoModels.TodoRow(
                entity.getTodoId(), entity.getParentTodoId(),
                entity.getOrder() == null ? 0 : entity.getOrder(),
                entity.getTitle(), entity.getMemo(), entity.getStatus(), entity.getPriority(),
                entity.getDueDate(),
                entity.getStartedAt() == null ? null : entity.getStartedAt().toString(),
                entity.getCompletedAt() == null ? null : entity.getCompletedAt().toString(),
                createdByName(entity.getCreatedByAccountId()),
                children.size(), doneChildren,
                entity.getVersion() == null ? 1 : entity.getVersion(),
                children);
    }

    /** 作成者の表示名（保護者が作った場合は保護者の名前を出す）。 */
    private String createdByName(Long accountId) {
        if (accountId == null) {
            return null;
        }
        AccountEntity account = accountMapper.findById(accountId);
        if (account == null) {
            return null;
        }
        String name = (nullToEmpty(account.getSei()) + nullToEmpty(account.getMei())).trim();
        return name.isEmpty() ? account.getLoginId() : name;
    }

    private String normalizePriority(String priority) {
        String code = code(priority, TodoModels.PRIORITIES, "優先度");
        return code == null ? "NORMAL" : code;
    }

    private String code(String value, List<String> allowed, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String upper = value.trim().toUpperCase();
        if (!allowed.contains(upper)) {
            throw new ValidationException(label + "の指定が正しくありません。");
        }
        return upper;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
