package com.cloudticket.common.web;

public record ApiError(String code, String message, String traceId) {}
