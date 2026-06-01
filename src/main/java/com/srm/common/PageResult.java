package com.srm.common;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页 */
    private long pageNum;

    /** 每页条数 */
    private long pageSize;

    /** 总条数 */
    private long total;

    /** 数据列表 */
    private List<T> records;

    public static <T> PageResult<T> of(long pageNum, long pageSize, long total, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setTotal(total);
        result.setRecords(records);
        return result;
    }
}
