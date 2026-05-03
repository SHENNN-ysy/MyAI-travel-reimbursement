package com.aidemo.myaitravelreimbursement.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果封装
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private long total;
    private long current;
    private long size;
    private List<T> records;

    public static <T> PageResult<T> of(long total, long current, long size, List<T> records) {
        return new PageResult<>(total, current, size, records);
    }

    public long getPages() {
        return size > 0 ? (total + size - 1) / size : 0;
    }

    public boolean hasNext() {
        return current < getPages();
    }

    public boolean hasPrevious() {
        return current > 1;
    }
}
