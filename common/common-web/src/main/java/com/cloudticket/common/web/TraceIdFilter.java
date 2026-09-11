package com.cloudticket.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;

public class TraceIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Trace-Id";
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String traceId = supplied != null && supplied.length() <= 64 && supplied.matches("[A-Za-z0-9._:-]+")
                ? supplied : UUID.randomUUID().toString();
        response.setHeader(HEADER, traceId);
        chain.doFilter(request, response);
    }
}
