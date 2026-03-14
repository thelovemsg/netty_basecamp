package org.example.netty_basecamp;

import org.example.netty_basecamp.netty.NettyBootcampServer;

public class NettyBaseCampApplication {
    public static void main(String[] args) throws Exception {
        new NettyBootcampServer(8080).start();
    }
}
