package com.nhochamvui.rtmp;

import com.nhochamvui.rtmp.core.Server;
import io.micronaut.runtime.Micronaut;

public class Application {

    public static void main(String[] args) {
        final var ctx = Micronaut.run(Application.class, args);
        final Server server = ctx.getBean(Server.class);
        server.listen();
    }
}