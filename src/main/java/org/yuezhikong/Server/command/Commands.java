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

package org.yuezhikong.Server.command;

import lombok.extern.slf4j.Slf4j;
import org.yuezhikong.Server.Server;
import org.yuezhikong.Server.ServerAPI;
import org.yuezhikong.Server.user.User;
import org.yuezhikong.Server.user.userInformation;
import org.yuezhikong.utils.SHA256;
import org.yuezhikong.utils.database.dao.userInformationDao;

import javax.security.auth.login.AccountNotFoundException;
import java.util.List;

/**
 * 内部命令实现类
 * 所有命令仅允许服务端（控制台）使用
 */
@Slf4j
public class Commands {

    /**
     * 帮助命令
     */
    public static class HelpCommand implements Command {
        @Override
        public boolean execute(String command, String[] args, User user) {
            if (args.length != 0) {
                return false;
            }
            Server serverInstance = Server.getInstance();
            ServerAPI serverAPI = serverInstance.getServerAPI();
            serverAPI.sendMessageToUser(user, "JavaIM服务器帮助");
            serverInstance.getRequest().getRegisterCommands().forEach(information ->
                    serverAPI.sendMessageToUser(user, information.commandInstance().getUsage() + " - " + information.commandInstance().getDescription())
            );
            return true;
        }

        @Override
        public String getDescription() {
            return "查询帮助";
        }

        @Override
        public String getUsage() {
            return "/help";
        }

        @Override
        public boolean isAllowBroadcastCommandRunning() {
            return true;
        }
    }

    /**
     * 列出在线用户命令
     */
    public static class ListCommand implements Command {
        @Override
        public boolean execute(String command, String[] args, User user) {
            if (args.length != 0) {
                return false;
            }
            Server serverInstance = Server.getInstance();
            ServerAPI serverAPI = serverInstance.getServerAPI();
            List<User> onlineUserList = serverAPI.getValidUserList(true);
            
            onlineUserList.forEach((u) ->
                    serverAPI.sendMessageToUser(user, String.format("%s", u.getUserName()))
            );
            return true;
        }

        @Override
        public String getDescription() {
            return "显示在线用户列表";
        }

        @Override
        public String getUsage() {
            return "/list";
        }

        @Override
        public boolean isAllowBroadcastCommandRunning() {
            return true;
        }
    }

    /**
     * 私聊命令
     */
    public static class TellCommand implements Command {
        @Override
        public boolean execute(String command, String[] args, User user) {
            if (args.length < 2) {
                return false;
            }
            Server serverInstance = Server.getInstance();
            ServerAPI serverAPI = serverInstance.getServerAPI();
            StringBuilder stringBuilder = new StringBuilder();
            for (String arg : args) {
                stringBuilder.append(arg).append(" ");
            }

            if (!stringBuilder.isEmpty()) {
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            }

            stringBuilder.delete(0, args[0].length() + 1);
            stringBuilder.insert(0, "[私聊] ");

            String chatMessage = stringBuilder.toString();

            if (args[0].equals("Server")) {
                log.info("[{}]:{}", user.getUserName(), chatMessage);
                serverAPI.sendMessageToUser(user, "你对" + args[0] + "发送了私聊：" + chatMessage);
                return true;
            }
            
            try {
                User targetUser = serverAPI.getUserByUserName(args[0]);
                serverAPI.sendMessageToUser(targetUser, 
                    String.format("[私聊] 来自 %s: %s", user.getUserName(), chatMessage));
                serverAPI.sendMessageToUser(user, "你对" + args[0] + "发送了私聊：" + chatMessage);
            } catch (AccountNotFoundException e) {
                serverAPI.sendMessageToUser(user, "此用户不存在");
            }
            return true;
        }

        @Override
        public String getDescription() {
            return "私聊某用户";
        }

        @Override
        public String getUsage() {
            return "/tell <目标用户> <消息>";
        }

        @Override
        public boolean isAllowBroadcastCommandRunning() {
            return false;
        }
    }

    /**
     * 关闭服务器命令
     */
    public static class QuitCommand implements Command {
        @Override
        public boolean execute(String command, String[] args, User user) {
            if (args.length != 0) {
                return false;
            }
            Server serverInstance = Server.getInstance();
            serverInstance.stop();
            return true;
        }

        @Override
        public String getDescription() {
            return "关闭服务器";
        }

        @Override
        public String getUsage() {
            return "/quit";
        }

        @Override
        public boolean isAllowBroadcastCommandRunning() {
            return true;
        }
    }

    /**
     * 修改用户密码命令
     */
    public static class ChangePasswordCommand implements Command {
        @Override
        public boolean execute(String command, String[] args, User user) {
            if (args.length != 2) {
                return false;
            }
            Server serverInstance = Server.getInstance();
            ServerAPI serverAPI = serverInstance.getServerAPI();

            userInformationDao mapper = serverInstance.getSqlSession().getMapper(userInformationDao.class);
            userInformation information = mapper.getUser(null, args[0], null, null);
            if (information == null) {
                serverAPI.sendMessageToUser(user, "您所操作的用户从来没有来到过本服务器");
                return true;
            }
            information.setPasswd(SHA256.sha256(args[1] + information.getSalt()));
            mapper.updateUser(information);
            serverAPI.sendMessageToUser(user, "操作成功完成。");
            return true;
        }

        @Override
        public String getDescription() {
            return "修改用户的密码";
        }

        @Override
        public String getUsage() {
            return "/change-password <目标用户> <密码>";
        }

        @Override
        public boolean isAllowBroadcastCommandRunning() {
            return false;
        }
    }

    /**
     * 踢出用户命令
     */
    public static class KickCommand implements Command {
        @Override
        public boolean execute(String command, String[] args, User user) {
            if (args.length != 1) {
                return false;
            }
            Server serverInstance = Server.getInstance();
            ServerAPI serverAPI = serverInstance.getServerAPI();

            User kickUser;
            try {
                kickUser = serverAPI.getUserByUserName(args[0]);
            } catch (AccountNotFoundException e) {
                serverAPI.sendMessageToUser(user, "此用户不存在");
                return true;
            }
            
            // 发送踢出消息
            serverAPI.sendMessageToUser(kickUser, "您已被踢出此服务器");
            String userName = kickUser.getUserName();
            
            // 如果是网络用户，先关闭网络连接
            if (kickUser instanceof org.yuezhikong.Server.user.NetworkUser) {
                org.yuezhikong.Server.user.NetworkUser networkUser = (org.yuezhikong.Server.user.NetworkUser) kickUser;
                networkUser.getNetworkClient().disconnect();
            }
            
            // 从服务器用户列表中移除
            kickUser.disconnect();
            serverAPI.sendMessageToUser(user, "已成功踢出用户：" + userName);
            return true;
        }

        @Override
        public String getDescription() {
            return "踢出用户";
        }

        @Override
        public String getUsage() {
            return "/kick <目标用户>";
        }

        @Override
        public boolean isAllowBroadcastCommandRunning() {
            return true;
        }
    }
}
