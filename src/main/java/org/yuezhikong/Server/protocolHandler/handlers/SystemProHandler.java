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

package org.yuezhikong.Server.protocolHandler.handlers;


import org.jetbrains.annotations.NotNull;
import org.yuezhikong.Server.Server;
import org.yuezhikong.Server.protocol.SystemProtocol;
import org.yuezhikong.Server.protocolHandler.ProtocolHandler;
import org.yuezhikong.Server.user.User;

public class SystemProHandler implements ProtocolHandler {
    @Override
    public void handleProtocol(@NotNull Server server, @NotNull String protocolData, User user) {
        SystemProtocol protocol = server.getGson().fromJson(protocolData, SystemProtocol.class);// 反序列化 json 到 object
        switch (protocol.getType()) { // 判断模式
            case "ChangePassword" -> responseChangePassReq(server, user, protocol.getMessage());
            case "Login", "Error", "DisplayMessage" -> {
                SystemProtocol systemProtocol = new SystemProtocol();
                systemProtocol.setType("Error");
                systemProtocol.setMessage("Invalid Protocol Type, Please Check it");
                server.getServerAPI().sendJsonToClient(user, server.getGson().toJson(systemProtocol), "SystemProtocol");
            }
            default -> server.getServerAPI().sendMessageToUser(user, "暂不支持此模式");
        }
    }


    /**
     * 处理修改密码请求
     * @param server    服务器实例
     * @param user      用户
     * @param newPass   新密码
     */
    private void responseChangePassReq(Server server, User  user, String newPass) {
        if (!checkLoginStatus(server, user)) // 检查登录状态
            return;
        server.getServerAPI().changeUserPassword(user, newPass);//修改密码
    }

    /**
     * 检查登录状态
     * @param server 服务器实例
     * @param user 用户
     * @return       是否登录
     */
    private boolean checkLoginStatus(Server server, User  user) {
        if (!user.isUserLogged()) {
            server.getServerAPI().sendMessageToUser(user, "请先登录");
            return false;
        }
        return true;
    }
}
