package com.aidemo.myaitravelreimbursement.common;

import com.aidemo.myaitravelreimbursement.entity.User;
import lombok.Data;

@Data
public class UserContext {

    private static final ThreadLocal<User> USER_HOLDER = new ThreadLocal<>();

    public static void setUser(User user) {
        USER_HOLDER.set(user);
    }

    public static User getUser() {
        return USER_HOLDER.get();
    }

    public static Long getUserId() {
        User user = USER_HOLDER.get();
        return user != null ? user.getId() : null;
    }

    public static void clear() {
        USER_HOLDER.remove();
    }
}
