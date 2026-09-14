package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页结果包装类
 *
 * @param <T> 数据记录类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页数据列表 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码 */
    private int page;

    /** 每页条数 */
    private int size;
}