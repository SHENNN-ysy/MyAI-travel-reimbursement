package com.aidemo.myaitravelreimbursement.service;

/**
 * 异步任务执行器服务接口
 * 单独拎出来避免 Spring AOP self-invocation 问题
 */
public interface AsyncTaskExecutorService {

    /**
     * 异步执行批量识别任务
     * @param taskId 任务ID
     */
    void executeBatchRecognizeTask(String taskId);
}
