package com.nhochamvui.rtmp

import com.nhochamvui.rtmp.core.Server
import io.micronaut.runtime.Micronaut
import groovy.transform.CompileStatic

@CompileStatic
class Application {

    static void main(String[] args) {
        Micronaut.run(Application, args)
        final server = new Server()
        server.listen()
    }
}
