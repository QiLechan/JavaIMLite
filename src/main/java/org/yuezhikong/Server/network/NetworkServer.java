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

import java.nio.charset.StandardCharsets;

@Slf4j
public class NetworkServer {
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private boolean isRunning = false;


    public void start(@Range(from = 1, to = 65535) int serverPort) {
        if (isRunning) {
            log.warn("Netty 服务端已经启动，请勿重复操作");
            return;
        }

        log.info("正在创建 Netty 线程池...");
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(4);

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class) // 使用 NIO 模式
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();


                            pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
                            pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));

                            pipeline.addLast(new SimpleChannelInboundHandler<String>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                                    log.info("【网络层】收到客户端[{}]的消息: {}", ctx.channel().remoteAddress(), msg);
                                    ctx.writeAndFlush("服务器已收到: " + msg + "\n");
                                }

                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    log.info("【网络层】有新客户端连入了: {}", ctx.channel().remoteAddress());
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) {
                                    log.info("【网络层】客户端断开连接: {}", ctx.channel().remoteAddress());
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    log.error("【网络层】通道发生异常", cause);
                                    ctx.close();
                                }
                            });
                        }
                    });

            log.info("正在绑定并监听端口: {}", serverPort);
            ChannelFuture f = b.bind(serverPort).sync();
            serverChannel = f.channel();
            isRunning = true;
            log.info("JavaIM Lite Netty 服务端启动成功！");

        } catch (Exception e) {
            log.error("Netty 启动失败！", e);
            stop();
        }
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    public void stop() {
        log.info("正在关闭 Lite 服务端...");
        isRunning = false;
        try {
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        } catch (InterruptedException ignored) {}

        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        log.info("服务端已安全关闭");
    }
}
