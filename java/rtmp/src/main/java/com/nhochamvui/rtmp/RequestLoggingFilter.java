package com.nhochamvui.rtmp;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;

@ServerFilter("/**")
public class RequestLoggingFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @RequestFilter
    public void filterRequest(HttpRequest<?> request) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);
        request.setAttribute("requestId", requestId);
        request.setAttribute("startTime", System.currentTimeMillis());

        String ip = "unknown";
        SocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress instanceof InetSocketAddress inetAddress && inetAddress.getAddress() != null) {
            ip = inetAddress.getAddress().getHostAddress();
        }
        String ua = request.getHeaders().get("User-Agent");
        if (ua == null) {
            ua = "unknown";
        }
        LOG.info("→ {} {} | IP: {} | UA: {}", request.getMethod(), request.getUri(), ip, ua);
    }

    @ResponseFilter
    public void filterResponse(HttpRequest<?> request, MutableHttpResponse<?> response) {
        long start = request.getAttribute("startTime", Long.class).orElse(System.currentTimeMillis());
        long duration = System.currentTimeMillis() - start;

        LOG.info("← {} {} | status={} | {}ms",
                request.getMethod(), request.getUri(), response.getStatus().getCode(), duration);

        MDC.remove("requestId");
    }
}