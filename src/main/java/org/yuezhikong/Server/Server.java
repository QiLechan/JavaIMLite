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

package org.yuezhikong.Server;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.yuezhikong.Main;
import org.yuezhikong.Server.network.ExitWatchdog;
import org.yuezhikong.Server.network.NetworkServer;
import org.yuezhikong.Server.protocol.ChatProtocol;
import org.yuezhikong.Server.protocol.GeneralProtocol;
import org.yuezhikong.Server.protocol.SystemProtocol;
import org.yuezhikong.Server.filetransfer.FileTransferRequestHandler;
import org.yuezhikong.Server.protocolHandler.ProtocolHandler;
import org.yuezhikong.Server.protocolHandler.handlers.ChatProHandler;
import org.yuezhikong.Server.protocolHandler.handlers.LoginProHandler;
import org.yuezhikong.Server.protocolHandler.handlers.SystemProHandler;
import org.yuezhikong.Server.protocolHandler.handlers.TransferProHandler;
import org.yuezhikong.Server.request.ChatRequest;
import org.yuezhikong.Server.user.User;
import org.yuezhikong.Server.user.UserAuthentication;
import org.yuezhikong.SystemConfig;
import org.yuezhikong.utils.database.DatabaseHelper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
public class Server {
    @Getter
    private SqlSession sqlSession;

    @Getter
    private ServerAPI serverAPI;

    @Getter
    private static Server Instance;

    @Getter
    private final List<User> users = new CopyOnWriteArrayList<>();

    @Getter
    private final Gson gson = new Gson();

    @Getter
    private NetworkServer networkServer;

    @Getter
    private ChatRequest request;

    @Getter
    private final ThreadGroup serverThreadGroup = Thread.currentThread().getThreadGroup();

    private final Map<String, ProtocolHandler> protocolHandlerMap = new ConcurrentHashMap<>();

    /**
     * GUI 模式标志。为 true 时跳过控制台输入线程，
     * 避免在无终端环境下 JLine 报 "句柄无效" 错误。
     */
    private boolean guiMode = false;

    private ScheduledExecutorService fileCleanupExecutor;

    /**
     * 设置 GUI 模式。必须在调用 {@link #start(int)} 之前设置。
     *
     * @param guiMode true 表示 GUI 模式（不启动控制台输入线程）
     */
    public void setGuiMode(boolean guiMode) {
        this.guiMode = guiMode;
    }

    public void start(int serverPort){
        networkServer =  new NetworkServer();
        log.info("正在启动JavaIM");
        Instance = this;
        // 初始化指令系统
        request = new ChatRequest();
        // 创建线程池
        ExecutorService ThreadPool = Executors.newCachedThreadPool();
        serverAPI = new ServerAPI(this) ;

            Future<?> DatabaseStartTask = ThreadPool.submit(() -> {
                log.info("正在启动数据库");
                String JDBCUrl;
                try {
                    JDBCUrl = DatabaseHelper.InitDataBase();
                } catch (Throwable throwable) {
                    log.error("数据库启动失败", throwable);
                    ThreadPool.shutdownNow();
                    log.error("JavaIM启动失败，因为数据库出错");
                    try {
                        stop();
                    } catch (NullPointerException ignored) {
                    }
                    log.info("JavaIM服务器已经关闭");
                    throw new RuntimeException(throwable);
                }
                sqlSession = DatabaseHelper.InitMybatis(JDBCUrl);
                log.info("数据库启动完成");
            });
            protocolHandlerMap.put("ChatProtocol", new ChatProHandler());
            protocolHandlerMap.put("LoginProtocol", new LoginProHandler());
            protocolHandlerMap.put("SystemProtocol", new SystemProHandler());
            protocolHandlerMap.put("TransferProtocol", new TransferProHandler());
            startFileCleanupTask();
            // GUI 模式下不启动控制台输入线程，避免 JLine 在无终端环境下报错
            if (!guiMode) {
            Thread ConsoleUserRequestThread = new Thread(() -> {
                Terminal terminal = Main.getTerminal();
                LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
                while (true) {
                    try {
                        String line = reader.readLine(">").trim();
                        if (line.isEmpty())
                            continue;
                        if (line.startsWith("/")) {
                            // 处理指令
                            String[] tmp = line.split("\\s+");
                            String command = tmp[0].substring(1);
                            String[] args = new String[tmp.length - 1];
                            System.arraycopy(tmp, 1, args, 0, tmp.length - 1);
                            request.commandRequest(command, args, new org.yuezhikong.Server.user.ConsoleUser());
                        } else {
                            // 聊天消息
                            log.info("[Server]: {}", line);
                            ChatProtocol chatProtocol = new ChatProtocol();
                            chatProtocol.setSourceUserName("Server");
                            chatProtocol.setMessage(line);
                            String SendProtocolData = gson.toJson(chatProtocol);
                            serverAPI.getValidUserList(true).forEach((user) ->
                                    serverAPI.sendJsonToClient(user, SendProtocolData, "ChatProtocol"));
                        }
                    } catch (Throwable throwable) {
                        if (throwable instanceof UserInterruptException) {
                            log.info("正在关闭JavaIM");
                            stop();
                            return;
                        }
                        if (throwable instanceof EndOfFileException) {
                            continue;
                        }
                        log.error("出现错误!", throwable);
                    }
                }
            });
            ConsoleUserRequestThread.start();
            } // end if (!guiMode)
            try {
                log.info("正在等待数据库启动完成");
                DatabaseStartTask.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Thread Pool Fatal", e);
            }
            networkServer.start(ThreadPool, serverPort);
            ThreadPool.shutdown();
    }

    public void disconnectUser(User user) {
        users.remove(user);
    }

    public boolean connectUser(User user) {
        // 如果用户名为null（未登录状态），直接添加
        if (user.getUserName() == null || user.getUserName().isEmpty()) {
            return users.add(user);
        }
        // 检查是否有重复的用户名
        for (User ForEachUser : users) {
            if (ForEachUser.getUserName() != null && ForEachUser.getUserName().equals(user.getUserName()))
                return false;
        }
        return users.add(user);
    }

    public void stop() {
        log.info("JavaIM服务器正在关闭...");
        getServerAPI().sendMessageToAllClient("服务器已关闭");
        users.clear();
        System.gc();
        networkServer.stop();
        Instance = null;
        stopFileCleanupTask();
        sqlSession.close();
        try {
            ExitWatchdog.getInstance().onExit();
        } catch (IllegalStateException ignored) {}
        log.info("JavaIM服务器已关闭");
    }

    public void onReceiveMessage(User user, String msg) {
        if (user.getUserAuthentication() == null)
            user.setUserAuthentication(new UserAuthentication(user, this));
        GeneralProtocol protocol;
        try {
            protocol = gson.fromJson(msg, GeneralProtocol.class);
        } catch (JsonSyntaxException e) {
            SystemProtocol systemProtocol = new SystemProtocol();
            systemProtocol.setType("Error");
            systemProtocol.setMessage("Protocol analysis failed");
            serverAPI.sendJsonToClient(user, gson.toJson(systemProtocol), "SystemProtocol");
            return;
        }
        if (protocol == null) {
            SystemProtocol systemProtocol = new SystemProtocol();
            systemProtocol.setType("Error");
            systemProtocol.setMessage("Invalid Packet");
            serverAPI.sendJsonToClient(user, gson.toJson(systemProtocol), "SystemProtocol");
            return;
        }
        if (protocol.getProtocolVersion() != SystemConfig.getProtocolVersion()) {
            SystemProtocol systemProtocol = new SystemProtocol();
            systemProtocol.setType("Error");
            systemProtocol.setMessage("Protocol version not support");
            serverAPI.sendJsonToClient(user, gson.toJson(systemProtocol), "SystemProtocol");
            return;
        }
        ProtocolHandler handler = protocolHandlerMap.get(protocol.getProtocolName());
        if (handler == null) {
            SystemProtocol systemProtocol = new SystemProtocol();
            systemProtocol.setType("Error");
            systemProtocol.setMessage("Protocol not support");
            serverAPI.sendJsonToClient(user, gson.toJson(systemProtocol), "SystemProtocol");
            return;
        }
        try {
            handler.handleProtocol(this, protocol.getProtocolData(), user);
        } catch (RuntimeException e) {
            log.error("Protocol handler failed", e);
            SystemProtocol systemProtocol = new SystemProtocol();
            systemProtocol.setType("Error");
            systemProtocol.setMessage("Protocol handling failed");
            serverAPI.sendJsonToClient(user, gson.toJson(systemProtocol), "SystemProtocol");
        }
    }

    private void startFileCleanupTask() {
        fileCleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "FileTransferCleanupThread");
            thread.setDaemon(true);
            return thread;
        });
        fileCleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                FileTransferRequestHandler.getInstance().cleanupExpiredRequests();
            } catch (Throwable throwable) {
                log.warn("File transfer cleanup failed", throwable);
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    private void stopFileCleanupTask() {
        if (fileCleanupExecutor != null) {
            fileCleanupExecutor.shutdownNow();
            fileCleanupExecutor = null;
        }
        FileTransferRequestHandler.getInstance().cleanupAllRequests();
    }

}
