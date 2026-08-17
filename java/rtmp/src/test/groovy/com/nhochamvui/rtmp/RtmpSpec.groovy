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

    void 'home page serves the streamer SPA shell'() {
        when:
        String html = client.toBlocking().retrieve(HttpRequest.GET("/"), String)

        then:
        html.contains('<div id="root">')
        html.contains('/static/assets/')
        html.contains('RTMP Server')
        !html.contains('private-publish-key')
    }
}
