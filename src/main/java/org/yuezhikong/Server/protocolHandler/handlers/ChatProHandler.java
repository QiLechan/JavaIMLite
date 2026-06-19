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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yuezhikong.Server.Server;
import org.yuezhikong.Server.protocol.ChatProtocol;
import org.yuezhikong.Server.protocolHandler.ProtocolHandler;
import org.yuezhikong.Server.user.User;

public class ChatProHandler implements ProtocolHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatProHandler.class);


    public void handleProtocol(@NotNull Server server, @NotNull String protocolData, User user) {
        if (!user.isUserLogged()){
            server.getServerAPI().sendMessageToUser(user, "请先登录喵~");
            return;
        }
        ChatProtocol protocol = server.getGson().fromJson(protocolData, ChatProtocol.class); // 反序列化 json 到 object
        
        // 检查是否为指令（以/开头），如果是则拒绝执行
        // 指令仅允许服务端（控制台）使用
        if (protocol.getMessage() != null && protocol.getMessage().startsWith("/")) {
            server.getServerAPI().sendMessageToUser(user, "指令仅允许在服务端控制台使用，客户端不支持指令功能");
            log.warn("用户 {} 尝试从客户端执行指令: {}", user.getUserName(), protocol.getMessage());
            return;
        }
        
        log.info("[{}]:{}", user.getUserName(), protocol.getMessage());// 打印消息到log

        ChatProtocol chatProtocol = new ChatProtocol();// 封装数据包发给所有用户
        chatProtocol.setSourceUserName(user.getUserName());
        chatProtocol.setMessage(protocol.getMessage());
        String SendProtocolData = server.getGson().toJson(chatProtocol);
        server.getServerAPI().getValidUserList(true).forEach((forEachUser) ->
                server.getServerAPI().sendJsonToClient(forEachUser, SendProtocolData, "ChatProtocol"));
    }
}
