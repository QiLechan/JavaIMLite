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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.NotNull;
import org.yuezhikong.Server.network.NetworkServer;
import org.yuezhikong.Server.protocol.GeneralProtocol;
import org.yuezhikong.Server.user.ConsoleUser;
import org.yuezhikong.Server.user.NetworkUser;
import org.yuezhikong.Server.user.User;
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
            networkServer.start(ThreadPool, serverPort);
        });
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
}
