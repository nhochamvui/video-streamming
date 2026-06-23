package com.nhochamvui.rtmp

import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.UUID

@ServerFilter("/**")
class RequestLoggingFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter)

    @RequestFilter
    void filterRequest(HttpRequest<?> request) {
        String requestId = UUID.randomUUID().toString().take(8)
        MDC.put("requestId", requestId)
        request.setAttribute("requestId", requestId)
        request.setAttribute("startTime", System.currentTimeMillis())

        String ip = request.remoteAddress?.address?.hostAddress ?: "unknown"
        String ua = request.headers.get("User-Agent") ?: "unknown"
        LOG.info("→ {} {} | IP: {} | UA: {}", request.method, request.uri, ip, ua)
    }

    @ResponseFilter
    void filterResponse(HttpRequest<?> request, MutableHttpResponse<?> response) {
        long start = request.getAttribute("startTime", Long.class).orElse(System.currentTimeMillis())
        long duration = System.currentTimeMillis() - start

        LOG.info("← {} {} | status={} | {}ms",
                request.method, request.uri, response.status.code, duration)

        MDC.remove("requestId")
    }
}
