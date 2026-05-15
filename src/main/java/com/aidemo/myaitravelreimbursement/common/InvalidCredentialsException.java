package com.aidemo.myaitravelreimbursement.common;

public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
    }
}
