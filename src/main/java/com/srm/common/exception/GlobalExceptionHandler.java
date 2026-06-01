package com.srm.common.exception;

import com.srm.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验异常 (POST/PUT @RequestBody) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = buildFieldError(e.getBindingResult().getFieldErrors());
        log.warn("参数校验失败: {}", msg);
        return Result.fail(400, msg);
    }

    /** 参数校验异常 (GET @RequestParam) */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBind(BindException e) {
        String msg = buildFieldError(e.getBindingResult().getFieldErrors());
        log.warn("参数校验失败: {}", msg);
        return Result.fail(400, msg);
    }

    /** 请求方法不支持（如对 POST 接口发 GET） */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的请求方法: {} {}", e.getMethod(), e.getSupportedHttpMethods());
        return Result.fail(405, "请求方法 " + e.getMethod() + " 不支持");
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.fail("系统繁忙，请稍后重试");
    }

    private String buildFieldError(java.util.List<FieldError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "参数校验失败";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            FieldError err = errors.get(i);
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(err.getField()).append(": ").append(err.getDefaultMessage());
        }
        return sb.toString();
    }
}
