/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.rpc.netty.ws;

import co.rsk.config.InternalService;
import co.rsk.rpc.netty.JsonRpcRequestHandler;
import co.rsk.rpc.netty.Jsonrpc4jLegacyHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetAddress;

public class Web3WebSocketServer implements InternalService {
    private static final Logger logger = LoggerFactory.getLogger(Web3WebSocketServer.class);

    private final InetAddress host;
    private final int port;
    private final JsonRpcRequestHandler.Factory requestHandlerFactory;
    private final Jsonrpc4jLegacyHandler jsonrpc4jLegacyHandler;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private @Nullable ChannelFuture webSocketChannel;

    public Web3WebSocketServer(
            InetAddress host,
            int port,
            JsonRpcRequestHandler.Factory requestHandlerFactory,
            Jsonrpc4jLegacyHandler jsonrpc4jLegacyHandler) {
        this.host = host;
        this.port = port;
        this.requestHandlerFactory = requestHandlerFactory;
        this.jsonrpc4jLegacyHandler = jsonrpc4jLegacyHandler;
        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = new NioEventLoopGroup();
    }

    @Override
    public void start() {
        logger.info("RPC WebSocket enabled");
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new HttpServerCodec());
                    p.addLast(new HttpObjectAggregator(1024 * 1024 * 5));
                    p.addLast(new WebSocketServerProtocolHandler("/websocket"));
                    p.addLast(requestHandlerFactory.newInstance());
                    p.addLast(jsonrpc4jLegacyHandler);
                    p.addLast(new Web3ResultWebSocketResponseHandler());
                }
            });
        webSocketChannel = b.bind(host, port);
        try {
            webSocketChannel.sync();
        } catch (InterruptedException e) {
            logger.error("The RPC WebSocket server couldn't be started", e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        try {
            webSocketChannel.channel().close().sync();
        } catch (InterruptedException e) {
            logger.error("Couldn't stop the RPC WebSocket server", e);
            Thread.currentThread().interrupt();
        }
        this.bossGroup.shutdownGracefully();
        this.workerGroup.shutdownGracefully();
    }
}
