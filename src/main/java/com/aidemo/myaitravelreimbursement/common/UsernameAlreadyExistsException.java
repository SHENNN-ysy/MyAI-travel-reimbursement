package com.aidemo.myaitravelreimbursement.common;

public class UsernameAlreadyExistsException extends BusinessException {

    public UsernameAlreadyExistsException(String username) {
        super(ErrorCode.BAD_REQUEST, "用户名【" + username + "】已被注册");
    }
}
