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

import com.google.gson.Gson;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.ReferenceCountUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.jetbrains.annotations.Range;
import org.yuezhikong.Server.Server;
import org.yuezhikong.Server.protocol.GeneralProtocol;
import org.yuezhikong.Server.protocol.SystemProtocol;
import org.yuezhikong.Server.user.CommonUser;
import org.yuezhikong.Server.user.NetworkUser;
import org.yuezhikong.Server.user.User;
import org.yuezhikong.SystemConfig;
import org.yuezhikong.utils.cert.Certificate;
import org.yuezhikong.utils.cert.CertificateInfo;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.SocketAddress;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.yuezhikong.utils.cert.Certificate.*;

@Slf4j
public class NetworkServer {
    /**
     * 设置用于接受客户端连接的parentGroup，以及处理客户端数据读写的workerGroup
     */
    private EventLoopGroup parentGroup, workerGroup;
    private DefaultEventLoopGroup RecvMessageThreadPool;
    private final List<NetworkClient> clientList = new ArrayList<>();
    private ChannelFuture future;
    private PrivateKey ServerSSLPrivateKey;
    private X509Certificate ServerSSLCertificate;
    private Channel serverChannel;

    private CertificateInfo setCertificateInfo(CertificateInfo info) {
        long currentTimeMillis = System.currentTimeMillis();
        Certificate.SubjectBuilder builder = new Certificate.SubjectBuilder();
        X500Name subject = builder
                .setCn(SystemConfig.getServerName())
                .setL("Shanghai")
                .setO("JavaIM-Server")
                .setSt("Shanghai")
                .setC("CN")
                .setOu(SystemConfig.getServerName())
                .build();
        info.setIssuer(subject);
        info.setSerial(BigInteger.valueOf(currentTimeMillis));
        info.setKeyAlgorithm(KEY_ALGORITHM);
        info.setNotBefore(new Date(currentTimeMillis));
        info.setNotAfter(new Date(currentTimeMillis + TimeUnit.DAYS.toMillis(365 * 10)));
        info.setSubject(subject);
        info.setSignAlgorithm(SIGN_ALGORITHM);
        return info;
    }

    private static String lf(String str, int length) {
        StringBuilder builder = new StringBuilder();
        char[] chars = str.toCharArray();
        int count = 0;
        for (char c : chars) {
            builder.append(c);
            count++;
            if (count == length) {
                builder.append("\n");
                count = 0;
            }
        }
        if (count != 0) {
            builder.append("\n");
        }
        return builder.toString();
    }

    private void X509CertificateGenerate() throws Throwable {
        CertificateInfo certinfo = new CertificateInfo();
        certinfo = setCertificateInfo(certinfo);
        Certificate.keyAndCertificate kc = generateCertificate(certinfo);
        byte[] privateKeyEncode = kc.privateKey().getEncoded();
        String privateKeyContent =
                Base64.getEncoder().encodeToString(privateKeyEncode);
        try {
            FileUtils.writeStringToFile(new File("./ServerEncryption/private.key"), privateKeyContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to Write private key.", e);
        }
        byte[] certificateEncode = kc.certificate().getEncoded();
        String certificateContent =
                "-----BEGIN CERTIFICATE-----\n" +
                        lf(Base64.getEncoder().encodeToString(certificateEncode), 64) +
                        "-----END CERTIFICATE-----";
        try {
            FileUtils.writeStringToFile(new File("./ServerEncryption/cert.crt"), certificateContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write cert to file.", e);
        }
        log.info("CA证书创建完成");
        log.info("请分发ServerEncryption文件夹中的cert.crt到各个客户端");
        log.info("请注意，ServerEncryption文件夹中的“private.key”如果泄露，您与客户端的连接将可能被劫持");
    }
    public void start(ExecutorService ThreadPool, @Range(from = 1, to = 65535) int serverPort){
        // Java 16 新特性
        record NettyThreadPoolTaskReturn(
                EventLoopGroup parentGroup,
                EventLoopGroup workerGroup,
                DefaultEventLoopGroup RecvMessageThreadPool) {
        }

        Future<?> CertTask = ThreadPool.submit(() -> {
            log.info("正在生成 X.509 SSL证书");
            try {
                X509CertificateGenerate();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> NettyThreadPoolTask = ThreadPool.submit(() -> {
            log.info("正在创建线程池");
            EventLoopGroup parentGroup = new NioEventLoopGroup(2);
            EventLoopGroup workerGroup = new NioEventLoopGroup(10);
            DefaultEventLoopGroup RecvMessageThreadPool = new DefaultEventLoopGroup(10);
            return new NettyThreadPoolTaskReturn(parentGroup, workerGroup, RecvMessageThreadPool);
        });

        try {
            NettyThreadPoolTaskReturn taskReturn = (NettyThreadPoolTaskReturn) NettyThreadPoolTask.get();
            parentGroup = taskReturn.parentGroup();
            workerGroup = taskReturn.workerGroup();
            RecvMessageThreadPool = taskReturn.RecvMessageThreadPool();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Thread Pool Fatal", e);
        }

        log.info("正在启动Netty");
        try {
            ServerBootstrap bs = new ServerBootstrap();
            bs.group(parentGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel channel) {
                            ChannelPipeline pipeline = channel.pipeline();
                            try {
                                CertTask.get();
                                pipeline.addLast(
                                        SslContextBuilder.forServer(ServerSSLPrivateKey, ServerSSLCertificate)
                                                .sslProvider(SslProvider.JDK)
                                                .clientAuth(ClientAuth.NONE)
                                                .build()
                                                .newHandler(channel.alloc())
                                );
                            } catch (SSLException | InterruptedException | ExecutionException e) {
                                throw new RuntimeException("SSL Context Generate Failed!", e);
                            }
                            pipeline.addLast(new LoggingHandler(LogLevel.DEBUG));
                            pipeline.addLast(new LineBasedFrameDecoder(Integer.MAX_VALUE));
                            pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
                            pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
                            pipeline.addLast(new MessageToMessageEncoder<CharSequence>() {
                                @Override
                                protected void encode(ChannelHandlerContext ctx, CharSequence msg, List<Object> out) {
                                    out.add(CharBuffer.wrap(msg + "\n"));
                                }
                            }); // 每行消息添加换行符
                            pipeline.addLast(RecvMessageThreadPool, new ServerHandler());
                        }
                    });
            future = bs.bind(serverPort).sync();
            log.info("JavaIM网络层启动完成");
            synchronized (NetworkServer.class) {
                NetworkServer.class.notifyAll();
            }
        } catch (InterruptedException e) {
            log.error("出现错误!", e);
        }
    }

    private class NetworkClient implements org.yuezhikong.Server.network.NetworkClient {
        @Getter
        private final NetworkUser user;

        private final SocketAddress address;
        private final Channel channel;

        private NetworkClient(NetworkUser user, SocketAddress address, Channel channel) {
            this.user = user;
            this.address = address;
            this.channel = channel;
        }

        @Override
        public SocketAddress getSocketAddress() {
            return address;
        }

        public void send(String message) throws IllegalStateException {
            //checks.checkState(!isOnline(), "This user is now offline!");
            channel.writeAndFlush(message);
        }

        public boolean isOnline() {
            return clientList.contains(this);
        }

        public void disconnect() {
            if (isOnline())
                channel.disconnect();
        }
    }

    private static class NettyUser extends CommonUser implements NetworkUser {
        private NetworkClient client;
        /**
         * 获取此用户对应的网络客户端
         *
         * @return 网络客户端
         */
        public NetworkClient getNetworkClient() {
            return client;
        }

        @Override
        public boolean isServer() {
            return false;
        }

        private void setNetworkClient(NetworkClient client) {
            this.client = client;
        }

        @Override
        public User onUserLogin(String UserName) {
            log.info(String.format("用户：%s(IP地址：%s) 登录完成", UserName, getNetworkClient().getSocketAddress()));
            return super.onUserLogin(UserName);
        }
    }
    private class ServerHandler extends ChannelInboundHandlerAdapter {
        private final HashMap<Channel, NetworkClient> clientNetworkClientPair = new HashMap<>();
        private final Gson gson = new Gson();

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            log.info("检测到新客户端连接...");
            log.info("IP地址：{}", ctx.channel().remoteAddress());
            NettyUser nettyUser = new NettyUser();
            if (!Server.getInstance().connectUser(nettyUser)) {
                ctx.channel().close();
                return;
            }
            NetworkClient client = new NetworkClient(nettyUser, ctx.channel().remoteAddress(), ctx.channel());
            nettyUser.setNetworkClient(client);
            clientNetworkClientPair.put(ctx.channel(), client);
            clientList.add(client);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.info("客户端断开连接...");
            NetworkClient Client = clientNetworkClientPair.remove(ctx.channel());
            if (Client != null) {
                Server.getInstance().disconnectUser(Client.getUser());
                clientList.remove(Client);
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof String Msg)) {
                log.info(String.format("客户端：%s 发送了非String消息：%s", ctx.channel().remoteAddress(), msg.toString()));
                return;
            }
            if (Msg.isEmpty()) {
                log.info(String.format("客户端：%s 发送了空消息", ctx.channel().remoteAddress()));
                SystemProtocol systemProtocol = new SystemProtocol();
                systemProtocol.setType("Error");
                systemProtocol.setMessage("Empty Packet");
                GeneralProtocol protocol = new GeneralProtocol();
                protocol.setProtocolVersion(SystemConfig.getProtocolVersion());
                protocol.setProtocolName("SystemProtocol");
                protocol.setProtocolData(gson.toJson(systemProtocol));
                ctx.writeAndFlush(gson.toJson(protocol));
                return;
            }
            NetworkClient thisClient = clientNetworkClientPair.get(ctx.channel());
            Server.getInstance().onReceiveMessage(thisClient.getUser(), Msg);
            ReferenceCountUtil.release(msg);
        }
    }

    public void stop() {
        log.info("JavaIM 网络层正在关闭...");
        future.channel().close();
        parentGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        RecvMessageThreadPool.shutdownGracefully();
        log.info("JavaIM 网络层关闭完成");
    }
}

