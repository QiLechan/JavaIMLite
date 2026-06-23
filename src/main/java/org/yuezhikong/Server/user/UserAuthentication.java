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

package org.yuezhikong.Server.user;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.NotNull;
import org.yuezhikong.Server.Server;
import org.yuezhikong.Server.ServerAPI;
import org.yuezhikong.Server.protocol.SystemProtocol;
import org.yuezhikong.utils.SHA256;
import org.yuezhikong.utils.database.dao.userInformationDao;

import javax.security.auth.login.AccountNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public final class UserAuthentication {

    //用户数据区
    private volatile boolean UserLogged = false;
    private String UserName = "";
    private final User user;

    //回调区
    private final List<UserRecall> LoginRecalls = new ArrayList<>();
    private final Object LoginRecallLock = new Object();

    private final ServerAPI serverAPI;

    interface UserRecall {
        void run(User User);
    }

    /**
     * 实例化用户Auth实现
     *
     * @param user   用户
     * @param server 服务器实例
     */
    public UserAuthentication(User user, Server server) {
        this.user = user;
        serverAPI = server.getServerAPI();
    }

    public boolean doLogin() {
        try {
            if (Logouted)
                return false;
            synchronized (LoginRecalls) {
                for (UserRecall recall : LoginRecalls) {
                    recall.run(user);
                }
                LoginRecalls.clear();
            }
            return true;
        } catch (Throwable throwable) {
            log.error("用户登录流程出错，出现异常", throwable);
            serverAPI.sendMessageToUser(user, "执行登录时出现内部错误，当前Unix时间：" + System.currentTimeMillis() + "请联系服务器管理员");
            return false;
        }
    }

    private boolean PostUserNameAndPasswordLogin(String UserName, userInformation information) {
        this.UserName = UserName;
        SqlSession sqlSession = Server.getInstance().getSqlSession();
        userInformationDao mapper = sqlSession.getMapper(userInformationDao.class);

        //发送给用户
        SystemProtocol protocolData = new SystemProtocol();
        protocolData.setType("Login");
        protocolData.setMessage(UserName);
        String json = new Gson().toJson(protocolData);
        serverAPI.sendJsonToClient(user, json, "SystemProtocol");
        //设置登录成功
        UserLogged = true;
        user.setUserInformation(information);
        user.onUserLogin(UserName);
        return true;
    }

    private boolean DoPasswordLogin0(String UserName, String Password) {
        if ("Server".equals(UserName)) {
            serverAPI.sendMessageToUser(user, "禁止使用受保护的用户名：Server");
            SystemProtocol protocol = new SystemProtocol();
            protocol.setType("Login");
            protocol.setMessage("Authentication Failed");
            String json = new Gson().toJson(protocol);
            serverAPI.sendJsonToClient(user, json, "SystemProtocol");
            return false;
        }
        if (UserName == null || Password == null || UserName.isEmpty() || Password.isEmpty()) {
            serverAPI.sendMessageToUser(user, "禁止使用空字符串！");
            SystemProtocol protocol = new SystemProtocol();
            protocol.setType("Login");
            protocol.setMessage("Authentication Failed");
            String json = new Gson().toJson(protocol);
            serverAPI.sendJsonToClient(user, json, "SystemProtocol");
            return false;
        }
        try {
            serverAPI.getUserByUserName(UserName);
            //说明目前是已经有同一名字的用户登录了
            //因此，禁止登录
            SystemProtocol protocol = new SystemProtocol();
            protocol.setType("Login");
            protocol.setMessage("Already Logged");
            String json = new Gson().toJson(protocol);
            serverAPI.sendJsonToClient(user, json, "SystemProtocol");
            return false;
        } catch (AccountNotFoundException ignored) {
        }
        try {
            SqlSession sqlSession = Server.getInstance().getSqlSession();
            userInformationDao mapper = sqlSession.getMapper(userInformationDao.class);
            userInformation userInformation = mapper.getUser(null, UserName, null, null);
            if (userInformation != null) {
                //登录代码
                String salt;
                String sha256;
                salt = userInformation.getSalt();
                //为保护安全，保存密码是加盐sha256，只有对密码处理后，才能进行比较
                sha256 = SHA256.sha256(Password + salt);
                if (userInformation.getPasswd().equals(sha256)) {
                    // 检查数据库更新
                    CheckDatabaseUpgrade(mapper, userInformation);
                    return PostUserNameAndPasswordLogin(UserName, userInformation);
                } else {
                    serverAPI.sendMessageToUser(user, "登录失败，用户名或密码错误");
                    SystemProtocol protocol = new SystemProtocol();
                    protocol.setType("Login");
                    protocol.setMessage("Authentication Failed");
                    String json = new Gson().toJson(protocol);
                    serverAPI.sendJsonToClient(user, json, "SystemProtocol");
                    return false;
                }
            } else {
                //注册代码
                String salt;
                userInformation tempInformation;
                do {
                    //寻找一个安全的盐
                    salt = UUID.randomUUID().toString();
                    tempInformation = mapper.getUser(null, null, null, salt);
                } while (tempInformation != null);
                //密码加盐并保存
                String sha256 = SHA256.sha256(Password + salt);
                userInformation = new userInformation();
                userInformation.setPasswd(sha256);
                userInformation.setSalt(salt);
                userInformation.setUserId("");
                userInformation.setUserName(UserName);

                mapper.addUser(userInformation);
                CheckDatabaseUpgrade(mapper, userInformation);
                return PostUserNameAndPasswordLogin(UserName, userInformation);
            }
        } catch (Throwable t) {
            log.error("出现错误!", t);
            SystemProtocol protocol = new SystemProtocol();
            protocol.setType("Login");
            protocol.setMessage("Authentication Failed");
            String json = new Gson().toJson(protocol);
            serverAPI.sendJsonToClient(user, json, "SystemProtocol");
            return false;
        }
    }

    /**
     * 检查数据库更新
     *
     * @param mapper          dao层操作方法
     * @param userInformation 用户信息
     */
    private void CheckDatabaseUpgrade(@NotNull userInformationDao mapper, @NotNull userInformation userInformation) {
        if (userInformation.getUserId() == null || userInformation.getUserId().isEmpty()) {// 如果没有分配用户ID
            String randomUUID = null;
            do {
                String tmpUUID = UUID.randomUUID().toString();
                if (mapper.getUser(tmpUUID, null, null, null) != null)
                    continue;
                randomUUID = tmpUUID;
            } while (randomUUID == null);
            userInformation.setUserId(randomUUID);
        }
        mapper.updateUser(userInformation);
    }

    public boolean doLogin(String UserName, String Password) {
        try {
            if (!DoPasswordLogin0(UserName, Password))
                return false;

            if (Logouted)
                return false;
            synchronized (LoginRecalls) {
                for (UserRecall recall : LoginRecalls) {
                    recall.run(user);
                }
                LoginRecalls.clear();
            }
            return true;
        } catch (Throwable throwable) {
            log.error("用户登录流程出错，出现异常", throwable);
            serverAPI.sendMessageToUser(user, "执行登录时出现内部错误，当前Unix时间：" + System.currentTimeMillis() + "请联系服务器管理员");
            return false;
        }
    }



    public boolean isLogin() {
        return UserLogged;
    }


    public void registerLoginRecall(UserRecall runnable) {
        if (!UserLogged) {
            synchronized (LoginRecallLock) {
                if (!UserLogged) {
                    LoginRecalls.add(runnable);
                }
            }
        } else {
            runnable.run(user);
        }
    }


    public String getUserName() {
        return UserName;
    }

    private volatile boolean Logouted = false;
    private final List<UserRecall> DisconnectRecall = new ArrayList<>();
    private final Object DisconnectRecallLock = new Object();


    public void registerLogoutRecall(UserRecall runnable) {
        if (!Logouted) {
            synchronized (DisconnectRecallLock) {
                if (!Logouted) {
                    DisconnectRecall.add(runnable);
                    return;
                }
            }
        }
        runnable.run(user);
    }

    public boolean doLogout() {
        if (!UserLogged || Logouted) {
            return false;
        }
        synchronized (DisconnectRecallLock) {
            if (!Logouted) {
                for (UserRecall runnable : DisconnectRecall) {
                    runnable.run(user);
                }
                DisconnectRecall.clear();
            } else {
                return false;
            }
        }
        Logouted = true;
        UserLogged = false;
        return true;
    }

}
