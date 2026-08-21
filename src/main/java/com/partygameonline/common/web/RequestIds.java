package com.partygameonline.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

public final class RequestIds {

    private RequestIds() {
    }

    public static String current(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        String mdcValue = MDC.get(RequestIdFilter.MDC_KEY);
        if (mdcValue != null && !mdcValue.isBlank()) {
            return mdcValue;
        }
        return "unknown";
    }
}
