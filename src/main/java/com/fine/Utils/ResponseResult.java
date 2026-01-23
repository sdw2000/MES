package com.fine.Utils;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseResult<T> {
    /**
     * 状态码
     */
    private Integer code;
    /**
     * 提示信息，如果有错误时，前端可以获取该字段进行提示
     */
    private String msg;
    /**
     * 查询到的结果数据，
     */
    private T data;

    // 静态工厂方法
    public static <T> ResponseResult<T> success() {
        return new ResponseResult<>(20000, "success", null);
    }

    public static <T> ResponseResult<T> success(T data) {
        return new ResponseResult<>(20000, "success", data);
    }

    public static <T> ResponseResult<T> success(String msg, T data) {
        return new ResponseResult<>(20000, msg, data);
    }

    public static <T> ResponseResult<T> fail(String msg) {
        return new ResponseResult<>(500, msg, null);
    }

    public static <T> ResponseResult<T> fail(Integer code, String msg) {
        return new ResponseResult<>(code, msg, null);
    }

    // error 方法作为 fail 的别名
    public static <T> ResponseResult<T> error(String msg) {
        return new ResponseResult<>(500, msg, null);
    }

    public static <T> ResponseResult<T> error(Integer code, String msg) {
        return new ResponseResult<>(code, msg, null);
    }

    public ResponseResult(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public ResponseResult(Integer code, T data) {
        this.code = code;
        this.data = data;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public ResponseResult(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;    }

    public ResponseResult() {
        // Default constructor for frameworks requiring no-arg constructor
    }
}
