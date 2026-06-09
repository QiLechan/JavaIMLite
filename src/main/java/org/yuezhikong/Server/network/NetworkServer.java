/*
 * Simplified Chinese (简体中文)
 *
 * 版权所有 (C) 2023 QiLechan <qilechan@outlook.com> 和本程序的贡献者
 *
 * 本程序是自由软件：你可以再分发之和/或依照由自由软件基金会发布的 GNU 通用公共许可证修改之，无论是版本 3 许可证，还是 3 任何以后版都可以。
 * 发布该程序是希望它能有用，但是并无保障;甚至连可销售和符合某个特定的目的都不保证。请参看 GNU 通用公共许可证，了解详情。
 * 你应该随程序获得一份 GNU 通用公共许可证的副本。如果没有，请看 <https://www.gnu.org/licenses/>。
 * English (英语)
 *
 * Copyright (C) 2023 QiLechan <qilechan@outlook.com> and contributors to this program
 *
 *  This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or 3 any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package org.yuezhikong.Server.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Range;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Slf4j
public class NetworkServer {
    //增加保存服务器通道和在线状态的变量
    private EventLoopGroup bossGroup, workerGroup;
    private DefaultEventLoopGroup RecvMessageThreadPool;
    private Channel serverChannel;
    private boolean isRunning = false;

    // 2. 搬入功能：用于在内存中统一管理所有在线客户端的并发 Map
    private final Map<ChannelId, NetworkClient> onlineClients = new ConcurrentHashMap<>();

    /**
     * 3. 搬入功能：客户端会话包装类
     */
    public static class NetworkClient {
        private final ChannelHandlerContext ctx;

        public NetworkClient(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        public SocketAddress getSocketAddress() {
            return ctx.channel().remoteAddress();
        }

        public void send(String message) throws IllegalStateException {
            if (!ctx.channel().isActive()) {
                throw new IllegalStateException("客户端已断开连接");
            }
            ctx.writeAndFlush(message + "\n");
        }

        public void disconnect() {
            ctx.close();
        }
    }

    public void start(ExecutorService ThreadPool, @Range(from = 1, to = 65535) int serverPort) {
        if (isRunning) {
            log.warn("服务端已经启动，请勿重复操作");
            return;
        }

        record NettyThreadPoolTaskReturn(
                EventLoopGroup bossGroup,
                EventLoopGroup workerGroup,
                DefaultEventLoopGroup RecvMessageThreadPool) {
        }

        Future<?> NettyThreadPoolTask = ThreadPool.submit(() -> {
            log.info("正在创建线程池");
            EventLoopGroup bossGroup = new NioEventLoopGroup(2);
            EventLoopGroup workerGroup = new NioEventLoopGroup(10);
            DefaultEventLoopGroup RecvMessageThreadPool = new DefaultEventLoopGroup(10);
            return new NettyThreadPoolTaskReturn(bossGroup, workerGroup, RecvMessageThreadPool);
        });

        try {
            NettyThreadPoolTaskReturn taskReturn = (NettyThreadPoolTaskReturn) NettyThreadPoolTask.get();
            bossGroup = taskReturn.bossGroup();
            workerGroup = taskReturn.workerGroup();
            RecvMessageThreadPool = taskReturn.RecvMessageThreadPool();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Thread Pool Fatal", e);
        }

        log.info("正在启动Netty");


        //以下是搬入并补全的 Netty 核心运行与事件监听代码
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            // 纯文本流水线
                            pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
                            pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));

                            // 客户端事件核心处理器
                            pipeline.addLast(new SimpleChannelInboundHandler<String>() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    // 客户端连入：包装并丢进在线列表
                                    NetworkClient client = new NetworkClient(ctx);
                                    onlineClients.put(ctx.channel().id(), client);
                                    log.info("【网络层】有客户端连入: {}，当前在线人数: {}", ctx.channel().remoteAddress(), onlineClients.size());
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                                    log.info("【网络层】收到来自[{}]的消息: {}", ctx.channel().remoteAddress(), msg);
                                    // 临时复读机，后续直接在此对接你的 protocol 层
                                    NetworkClient client = onlineClients.get(ctx.channel().id());
                                    if (client != null) {
                                        client.send("Lite服务端收到你的小纸条: " + msg);
                                    }
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) {
                                    // 客户端断开：移出在线列表
                                    onlineClients.remove(ctx.channel().id());
                                    log.info("【网络层】客户端断开: {}，当前在线人数: {}", ctx.channel().remoteAddress(), onlineClients.size());
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    log.error("【网络层】通道异常: {}", cause.getMessage());
                                    ctx.close();
                                }
                            });
                        }
                    });

            log.info("正在绑定并监听端口: {}", serverPort);
            ChannelFuture f = b.bind(serverPort).sync();
            serverChannel = f.channel();
            isRunning = true;
            log.info("JavaIM Lite Netty 网络内核成功跑起来了！");

        } catch (Exception e) {
            log.error("Netty 绑定端口或运行期间发生崩溃！", e);
            stop();
        }
    }

    /**
     * 5. 搬入功能：获取当前在线客户端列表的能力
     */
    public NetworkClient[] getOnlineClients() {
        return onlineClients.values().toArray(new NetworkClient[0]);
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    /**
     * 6. 搬入功能：停机，释放线程池与端口
     */
    public void stop() {
        if (!isRunning) return;
        log.info("正在关闭网络层...");
        isRunning = false;

        for (NetworkClient client : onlineClients.values()) {
            client.disconnect();
        }
        onlineClients.clear();

        try {
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        } catch (InterruptedException ignored) {}

        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        if (RecvMessageThreadPool != null) RecvMessageThreadPool.shutdownGracefully();
        log.info("网络层已彻底关闭释放");
    }
}

