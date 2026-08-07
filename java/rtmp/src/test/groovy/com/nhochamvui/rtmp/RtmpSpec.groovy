package com.nhochamvui.rtmp

import io.micronaut.runtime.EmbeddedApplication
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification
import jakarta.inject.Inject

@MicronautTest
class RtmpSpec extends Specification {

    @Inject
    EmbeddedApplication<?> application

    @Inject
    @Client("/")
    HttpClient client

    void 'test it works'() {
        expect:
        application.running
    }

    void 'home page renders streamer session UI contract'() {
        when:
        String html = client.toBlocking().retrieve(HttpRequest.GET("/"), String)

        then:
        html.contains("Create stream session")
        html.contains("/api/v1/stream-sessions")
        html.contains("serverUrl")
        html.contains("streamKey")
        html.contains("playbackUrl")
        html.contains("expiresInSeconds")
        !html.contains("private-publish-key")
    }
}
