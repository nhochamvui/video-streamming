package com.nhochamvui.rtmp

import com.nhochamvui.rtmp.core.Server
import io.micronaut.runtime.Micronaut
import groovy.transform.CompileStatic

@CompileStatic
class Application {

    static void main(String[] args) {
        final ctx = Micronaut.run(Application, args)
        final streamKey = ctx.getProperty("rtmp.stream-key", String.class, "")
        final server = new Server()
        server.setStreamKey(streamKey)
        server.listen()
    }
}
