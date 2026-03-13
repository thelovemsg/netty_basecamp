package org.example.netty_basecamp;

import org.example.netty_basecamp.netty.NettyEchoServer;

public class NettyBaseCampApplication {
    public static void main(String[] args) throws InterruptedException {
        new NettyEchoServer(8080).start();
    }
}
