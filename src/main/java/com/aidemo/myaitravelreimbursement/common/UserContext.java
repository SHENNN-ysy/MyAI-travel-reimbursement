package com.aidemo.myaitravelreimbursement.common;

import com.aidemo.myaitravelreimbursement.entity.User;
import lombok.Data;

@Data
public class UserContext {

    private static final ThreadLocal<User> USER_HOLDER = new ThreadLocal<>();

    /**
     * 由 Agent 执行器在启动工具调用前写入，供 LangChain4j 线程池中的工具执行线程读取。
     * 不使用 ThreadLocal 而用普通字段，因为写入和读取都在同一虚拟线程或线程池上下文。
     */
    private static volatile Snapshot currentSnapshot;

    public static void setUser(User user) {
        USER_HOLDER.set(user);
    }

    public static User getUser() {
        return USER_HOLDER.get();
    }

    public static Long getUserId() {
        User user = USER_HOLDER.get();
        if (user != null) return user.getId();
        if (currentSnapshot != null) return currentSnapshot.getUserId();
        return null;
    }

    /**
     * 由 Agent 执行器在启动 Agent 对话前设置，工具执行线程读取此快照恢复上下文。
     */
    public static void setCurrentSnapshot(Snapshot snapshot) {
        currentSnapshot = snapshot;
    }

    public static Snapshot getCurrentSnapshot() {
        return currentSnapshot;
    }

    /**
     * 清除当前快照（Agent 对话结束后调用）。
     */
    public static void clearCurrentSnapshot() {
        currentSnapshot = null;
    }

    public static void clear() {
        USER_HOLDER.remove();
    }

    /**
     * 用户上下文快照，用于跨线程传递。
     * 在发起异步调用前使用 capture() 捕获，异步线程中通过 restore() 恢复。
     */
    public static class Snapshot {
        private final Long userId;
        private final String username;

        private Snapshot(Long userId, String username) {
            this.userId = userId;
            this.username = username;
        }

        public Long getUserId() { return userId; }
        public String getUsername() { return username; }

        public void restore() {
            if (userId != null) {
                User user = new User();
                user.setId(userId);
                user.setUsername(username);
                UserContext.setUser(user);
            }
        }
    }

    /**
     * 捕获当前用户上下文为快照（用于跨线程传递）。
     * 必须在主线程中调用。
     */
    public static Snapshot capture() {
        User user = USER_HOLDER.get();
        return new Snapshot(
                user != null ? user.getId() : null,
                user != null ? user.getUsername() : null
        );
    }

    /**
     * 从快照恢复用户上下文（在新线程中调用）。
     */
    public static void restore(Snapshot snapshot) {
        if (snapshot != null) {
            snapshot.restore();
        }
    }
}
