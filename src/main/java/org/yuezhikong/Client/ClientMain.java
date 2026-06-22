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

package org.yuezhikong.Client;

import com.google.gson.Gson;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.yuezhikong.Main;
import org.yuezhikong.Server.protocol.*;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ClientMain {
    protected static final int protocolVersion = 1;
    private final Gson gson = new Gson();

    protected String UserName;
    private String Passwd;

    protected ThreadFactory getWorkerThreadFactory() {
        return new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            private final ThreadGroup IOThreadGroup = new ThreadGroup(Thread.currentThread().getThreadGroup(), "IO Thread Group");

            @Override
            public Thread newThread(@NotNull Runnable r) {
                return new Thread(IOThreadGroup,
                        r,"Netty Worker Thread #"+threadNumber.getAndIncrement());
            }
        };
    }

    public void start(String serverAddress, int serverPort, String userName, String password, X509Certificate ServerCACert) {
        Terminal terminal = Main.getTerminal();
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();

        Thread UserChatThread = new Thread(() -> {
            while (true) {
                //Scanner scanner = new Scanner(System.in);
                String Data = reader.readLine(">");
                
                // 检查是否为文件上传指令
                if (Data.startsWith("/upload")) {
                    handleUploadCommand(Data);
                    continue;
                }
                
                // 检查是否为接受/拒绝文件传输指令
                if (Data.startsWith("/y ") || Data.startsWith("/n ")) {
                    handleFileTransferResponse(Data);
                    continue;
                }
                
                ChatProtocol userInput = new ChatProtocol();
                userInput.setMessage(Data);

                GeneralProtocol generalProtocol = new GeneralProtocol();
                generalProtocol.setProtocolData(gson.toJson(userInput));
                generalProtocol.setProtocolVersion(protocolVersion);
                generalProtocol.setProtocolName("ChatProtocol");

                sendData(gson.toJson(generalProtocol));
            }
        });
        UserChatThread.setDaemon(true);
        UserChatThread.start();

        UserName = userName;
        Passwd = password;

        EventLoopGroup workGroup = new NioEventLoopGroup(getWorkerThreadFactory());

        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(workGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            // 创建自定义 TrustManager，跳过主机名验证
                            X509ExtendedTrustManager customTrustManager = new X509ExtendedTrustManager() {
                                private final X509ExtendedTrustManager delegate;
                                
                                {
                                    // 初始化默认的 TrustManager
                                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                                    tmf.init((KeyStore) null);
                                    X509TrustManager defaultTm = (X509TrustManager) tmf.getTrustManagers()[0];
                                    
                                    // 获取 CA 证书并初始化
                                    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                                    keyStore.load(null, null);
                                    keyStore.setCertificateEntry("ca", ServerCACert);
                                    
                                    tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                                    tmf.init(keyStore);
                                    this.delegate = (X509ExtendedTrustManager) tmf.getTrustManagers()[0];
                                }
                                
                                @Override
                                public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
                                    delegate.checkClientTrusted(chain, authType, socket);
                                }
                                
                                @Override
                                public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
                                    // 跳过主机名验证，只验证证书链
                                    delegate.checkServerTrusted(chain, authType);
                                }
                                
                                @Override
                                public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
                                    delegate.checkClientTrusted(chain, authType, engine);
                                }
                                
                                @Override
                                public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
                                    // 跳过主机名验证，只验证证书链
                                    delegate.checkServerTrusted(chain, authType);
                                }
                                
                                @Override
                                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                                    delegate.checkClientTrusted(chain, authType);
                                }
                                
                                @Override
                                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                                    delegate.checkServerTrusted(chain, authType);
                                }
                                
                                @Override
                                public X509Certificate[] getAcceptedIssuers() {
                                    return delegate.getAcceptedIssuers();
                                }
                            };
                            
                            ch.pipeline().addLast(SslContextBuilder.forClient()
                                    .trustManager(customTrustManager)
                                    .build().newHandler(ch.alloc()));
                            ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));// 对于每条Channel消息打印debug级别日志
                            ch.pipeline().addLast(new LineBasedFrameDecoder(Integer.MAX_VALUE));// 根据回车分割消息
                            ch.pipeline().addLast(new StringEncoder(StandardCharsets.UTF_8), new StringDecoder(StandardCharsets.UTF_8));// 处理文本为String
                            ch.pipeline().addLast(new MessageToMessageEncoder<CharSequence>() {
                                @Override
                                protected void encode(ChannelHandlerContext ctx, CharSequence msg, List<Object> out) {
                                    out.add(CharBuffer.wrap(msg + "\n"));
                                }
                            });// 每行消息添加换行符
                            ch.pipeline().addLast(new ClientHandler());
                        }
                    });
            ChannelFuture future = bootstrap.connect(serverAddress, serverPort).sync();
            channel = future.channel();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            normalPrint("主线程受到中断，程序已结束");
        } finally {
            workGroup.shutdownGracefully();
        }
    }

    private void sendData(String Data) {
        channel.writeAndFlush(Data);
    }

    protected Channel channel;

    public void disconnect()
    {
        channel.close();
    }

    private void errorPrint(String data){
        log.error(data);
    }

    private class ClientHandler extends ChannelInboundHandlerAdapter
    {
        private final Gson gson = new Gson();

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            cause.printStackTrace(pw);

            errorPrint("出现未捕获的错误");
            errorPrint(sw.toString());
            disconnect();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            LoginProtocol loginPacket;
                LoginProtocol.LoginPacketHeadBean headBean = new LoginProtocol.LoginPacketHeadBean();
                headBean.setType("passwd");

                LoginProtocol.LoginPacketBodyBean bodyBean = new LoginProtocol.LoginPacketBodyBean();
                LoginProtocol.LoginPacketBodyBean.NormalLoginBean normalLoginBean = new LoginProtocol.LoginPacketBodyBean.NormalLoginBean();
                normalLoginBean.setUserName(UserName);
                normalLoginBean.setPasswd(Passwd);
                bodyBean.setNormalLogin(normalLoginBean);

                loginPacket = new LoginProtocol();
                loginPacket.setLoginPacketHead(headBean);
                loginPacket.setLoginPacketBody(bodyBean);

            GeneralProtocol generalProtocol = new GeneralProtocol();
            generalProtocol.setProtocolName("LoginProtocol");
            generalProtocol.setProtocolVersion(protocolVersion);
            generalProtocol.setProtocolData(gson.toJson(loginPacket));
            ctx.writeAndFlush(gson.toJson(generalProtocol));
        }

        private void HandleSystemProtocol(ChannelHandlerContext ctx, SystemProtocol protocol) throws IOException {
            switch (protocol.getType())
            {
                case "Error": {
                    //onError(protocol);
                    break;
                }
                case "DisplayMessage" : {
                    displayMessage(protocol.getMessage());
                    break;
                }
                case "Login" : {
                    if ("Authentication Failed".equals(protocol.getMessage()))
                    {
                        normalPrint("登录失败，用户名、密码错误");
                        ctx.channel().close();
                    } else if ("Already Logged".equals(protocol.getMessage()))
                        normalPrint("操作失败，已经登录过了");
                    else {
                        if ("Success".equals(protocol.getMessage())) {
                            normalPrint("登录成功!");
                        } else {
                            normalPrintf("登录成功! ");
                        }
                        //onClientLogin();
                    }
                    break;
                }
            }
        }
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try
            {
                if (!(msg instanceof String Msg))
                    return;
                GeneralProtocol protocol = gson.fromJson(Msg, GeneralProtocol.class);
                if (protocol.getProtocolVersion() != protocolVersion)
                {
                    errorPrint("服务器协议版本与当前客户端不一致");
                    errorPrintf("服务器版本:%s，当前客户端版本:%s%n",protocol.getProtocolVersion(),protocolVersion);
                    errorPrint("客户端已经断开与服务器的连接...");
                    ctx.channel().close();
                }
                switch (protocol.getProtocolName())
                {
                    case "SystemProtocol":
                    {
                        HandleSystemProtocol(ctx,gson.fromJson(protocol.getProtocolData(), SystemProtocol.class));
                        break;
                    }
                    case "ChatProtocol" : {
                        ChatProtocol chatProtocol = gson.fromJson(protocol.getProtocolData(), ChatProtocol.class);
                        displayChatMessage(chatProtocol.getSourceUserName(), chatProtocol.getMessage());
                        break;
                    }
                    case "TransferProtocol": {
                        TransferProtocol transferProtocol = gson.fromJson(protocol.getProtocolData(), TransferProtocol.class);

                        switch (transferProtocol.getTransferProtocolHead().getType()) {
                            case "fileList" : {
                                //caughtFileSystemReport("fileList",transferProtocol);
                                break;
                            }

                            case "download" : {
                                List<TransferProtocol.TransferProtocolBodyBean> bodyBeans = transferProtocol.getTransferProtocolBody();
                                String fileName = bodyBeans.get(0).getData();
                                if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                                    String randomName = UUID.randomUUID().toString();
                                    errorPrintf("服务端发送的文件: %s 中存在非法字符，自动重命名为%s", fileName, randomName);
                                    fileName = randomName;
                                }
                                String base64Content = bodyBeans.get(1).getData();
                                byte[] content = Base64.getDecoder().decode(base64Content);

                                // 创建downloads目录
                                File downloadDir = new File("./downloads/");
                                if (!downloadDir.exists()) {
                                    downloadDir.mkdirs();
                                }
                                
                                // 保存文件
                                File destFile = new File(downloadDir, fileName);
                                Files.write(destFile.toPath(), content);
                                normalPrintf("文件已保存: %s (%d bytes)%n", destFile.getAbsolutePath(), content.length);
                                break;
                            }
                            
                            case "upload_request" : {
                                // 处理文件上传请求
                                List<TransferProtocol.TransferProtocolBodyBean> bodyBeans = transferProtocol.getTransferProtocolBody();
                                String fileName = bodyBeans.get(0).getData();
                                String fileSize = bodyBeans.get(1).getData();
                                String requestId = bodyBeans.get(2).getData();
                                String sender = bodyBeans.get(3).getData();
                                
                                normalPrintf("%n[文件传输请求]%n");
                                normalPrintf("发送者: %s%n", sender);
                                normalPrintf("文件名: %s%n", fileName);
                                normalPrintf("文件大小: %s bytes%n", fileSize);
                                normalPrintf("请求ID: %s%n", requestId);
                                normalPrintf("输入 /y %s 接受文件，输入 /n %s 拒绝文件%n", requestId, requestId);
                                break;
                            }

                            default : {
                                errorPrintf("服务端发送的 TransferProtocol 模式%s 当前客户端不兼容服务端发送的 %s 模式",
                                        transferProtocol.getTransferProtocolHead().getType(),
                                        transferProtocol.getTransferProtocolHead().getType());
                                return;
                            }
                        }
                        break;
                    }
                    default: {
                        errorPrintf("服务器发送的协议为 %s 但是当前客户端不支持此协议%n", protocol.getProtocolName());
                        break;
                    }
                }
            } catch (Throwable throwable)
            {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                throwable.printStackTrace(pw);
                errorPrint(sw.toString());
            }
            finally {
                ReferenceCountUtil.release(msg);
            }
        }
    }
    protected void normalPrint(String data) {
        System.out.println(data);
    }

    protected void displayChatMessage(String sourceUserName, String message) {
        normalPrintf("[%s]:%s%n",sourceUserName,message);
    }
    protected void displayMessage(String message) {
        normalPrintf("%s%n",message);
    }
    protected void normalPrintf(String data, Object... args) {
        System.out.printf(data,args);
    }

    protected void errorPrintf(String data, Object... args) {
        System.err.printf(data,args);
    }
    
    /**
     * 处理客户端的文件上传指令
     */
    private void handleUploadCommand(String command) {
        String[] parts = command.split("\\s+", 2);
        if (parts.length < 2) {
            normalPrint("语法错误! 正确的语法为：/upload <文件路径>");
            return;
        }
        
        String filePath = parts[1];
        File file = new File(filePath);
        
        // 检查文件是否存在
        if (!file.exists()) {
            normalPrint("文件不存在: " + filePath);
            return;
        }
        
        // 检查是否是文件
        if (!file.isFile()) {
            normalPrint("路径不是一个文件: " + filePath);
            return;
        }
        
        // 检查文件大小（限制为10MB）
        long fileSize = file.length();
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (fileSize > maxSize) {
            normalPrint("文件太大，最大支持10MB");
            return;
        }
        
        try {
            // 读取文件内容并编码为Base64
            byte[] fileContent = Files.readAllBytes(file.toPath());
            String base64Content = Base64.getEncoder().encodeToString(fileContent);
            
            // 发送文件到服务器（使用ChatProtocol发送指令）
            ChatProtocol chatProtocol = new ChatProtocol();
            chatProtocol.setMessage(command); // 发送/upload指令
            
            GeneralProtocol generalProtocol = new GeneralProtocol();
            generalProtocol.setProtocolData(gson.toJson(chatProtocol));
            generalProtocol.setProtocolVersion(protocolVersion);
            generalProtocol.setProtocolName("ChatProtocol");
            
            sendData(gson.toJson(generalProtocol));
            
        } catch (Exception e) {
            errorPrint("文件上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理文件传输响应（接受/拒绝）
     */
    private void handleFileTransferResponse(String command) {
        // 直接发送指令到服务器
        ChatProtocol chatProtocol = new ChatProtocol();
        chatProtocol.setMessage(command);
        
        GeneralProtocol generalProtocol = new GeneralProtocol();
        generalProtocol.setProtocolData(gson.toJson(chatProtocol));
        generalProtocol.setProtocolVersion(protocolVersion);
        generalProtocol.setProtocolName("ChatProtocol");
        
        sendData(gson.toJson(generalProtocol));
    }
}
