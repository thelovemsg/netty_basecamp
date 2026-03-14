package org.example.netty_basecamp.netty.channel;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CustomChannelInboundHandler implements ChannelHandler {
    final Logger logger = LogManager.getLogger();

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

    }
}
