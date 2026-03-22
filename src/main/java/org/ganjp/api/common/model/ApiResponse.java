package org.ganjp.api.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private Status status;
    private T data;
    private Meta meta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Status {
        private int code;
        private String message;
        private Object errors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private String serverDateTime;
        private String requestId;
        private String sessionId;
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .status(Status.builder()
                        .code(200)
                        .message(message)
                        .errors(null)
                        .build())
                .data(data)
                .meta(Meta.builder()
                        .serverDateTime(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                .format(java.time.LocalDateTime.now()))
                        .requestId(org.ganjp.api.common.util.RequestUtils.getCurrentRequestId())
                        .sessionId(org.ganjp.api.common.util.RequestUtils.getCurrentSessionId())
                        .build())
                .build();
    }

    public static <T> ApiResponse<T> error(int code, String message, Object errors) {
        return ApiResponse.<T>builder()
                .status(Status.builder()
                        .code(code)
                        .message(message)
                        .errors(errors)
                        .build())
                .data(null)
                .meta(Meta.builder()
                        .serverDateTime(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                .format(java.time.LocalDateTime.now()))
                        .requestId(org.ganjp.api.common.util.RequestUtils.getCurrentRequestId())
                        .sessionId(org.ganjp.api.common.util.RequestUtils.getCurrentSessionId())
                        .build())
                .build();
    }
}