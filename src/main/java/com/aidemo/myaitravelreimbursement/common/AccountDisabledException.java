package com.aidemo.myaitravelreimbursement.common;

public class AccountDisabledException extends BusinessException {

    public AccountDisabledException() {
        super(ErrorCode.FORBIDDEN, "账号已被禁用，请联系管理员");
    }
}
