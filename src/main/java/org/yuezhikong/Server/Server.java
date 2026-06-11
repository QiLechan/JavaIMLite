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
import org.yuezhikong.Server.network.NetworkServer;
import org.yuezhikong.Server.protocol.ChatProtocol;
import org.yuezhikong.Server.protocol.GeneralProtocol;
import org.yuezhikong.Server.protocol.SystemProtocol;
import org.yuezhikong.Server.protocolHandler.ProtocolHandler;
import org.yuezhikong.Server.protocolHandler.handlers.ChatProHandler;
import org.yuezhikong.Server.protocolHandler.handlers.LoginProHandler;
import org.yuezhikong.Server.protocolHandler.handlers.SystemProHandler;
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

    private final List<User> users = new CopyOnWriteArrayList<>();

    @Getter
    private final Gson gson = new Gson();

    @Getter
    private NetworkServer networkServer;

    @Getter
    private final ThreadGroup serverThreadGroup = Thread.currentThread().getThreadGroup();

    private final Map<String, ProtocolHandler> protocolHandlerMap = new ConcurrentHashMap<>();

    public void start(int serverPort){
        networkServer =  new NetworkServer();
        log.info("正在启动JavaIM");
        Instance = this;
        // 创建线程池
        ExecutorService ThreadPool = Executors.newCachedThreadPool();
        serverAPI = new ServerAPI(this) ;

        new Thread(() -> {
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
                    return;
                }
                sqlSession = DatabaseHelper.InitMybatis(JDBCUrl);
                log.info("数据库启动完成");
            });
            protocolHandlerMap.put("ChatProtocol", new ChatProHandler());
            protocolHandlerMap.put("LoginProtocol", new LoginProHandler());
            protocolHandlerMap.put("SystemProtocol", new SystemProHandler());
            Thread ConsoleUserRequestThread = new Thread(() -> {
                Terminal terminal = Main.getTerminal();
                LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
                while (true) {
                    try {
                        String line = reader.readLine(">").trim();
                        if (line.isEmpty())
                            continue;
                        if (!line.startsWith("/")) {
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
            try {
                log.info("正在等待数据库启动完成");
                DatabaseStartTask.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Thread Pool Fatal", e);
            }
            ThreadPool.shutdownNow();
        }).start();
        networkServer.start(ThreadPool, serverPort);
    }

    public void disconnectUser(User user) {
        users.remove(user);
    }

    public boolean connectUser(User user) {
        for (User ForEachUser : users) {
            if (ForEachUser.getUserName().equals(user.getUserName()))
                return false;
        }
        return users.add(user);
    }

    public List<User> getUsers() {
        return List.of();
    }

    public void stop() {

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
        if (protocol.getProtocolVersion() != SystemConfig.getProtocolVersion()) {
            SystemProtocol systemProtocol = new SystemProtocol();
            systemProtocol.setType("Error");
            systemProtocol.setMessage("Protocol version not support");
            serverAPI.sendJsonToClient(user, gson.toJson(systemProtocol), "SystemProtocol");
            return;
        }
        ProtocolHandler handler = protocolHandlerMap.get(protocol.getProtocolName());
        handler.handleProtocol(this, protocol.getProtocolData(), user);
    }
}
