package com.nhochamvui.rtmp

import com.nhochamvui.rtmp.core.Server
import io.micronaut.runtime.Micronaut
import groovy.transform.CompileStatic

@CompileStatic
class Application {

    static void main(String[] args) {
        final ctx = Micronaut.run(Application, args)
        final server = ctx.getBean(Server)
        server.listen()
    }
}
