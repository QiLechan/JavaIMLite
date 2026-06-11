package org.yuezhikong.Server.protocolHandler.Handlers;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yuezhikong.Server.Server;
import org.yuezhikong.Server.protocol.ChatProtocol;
import org.yuezhikong.Server.protocolHandler.ProtocolHandler;
import org.yuezhikong.Server.user.User;

public class ChatProHandler implements ProtocolHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatProHandler.class);
    private static final int MaxMessageLength = 500;
    private static final int MinMessageLength = 1;

    public void handleProtocol(@NotNull Server server, @NotNull String protocolData, User user) {
        if (user.isUserLogged()){
            server.getServerAPI().sendMessageToUser(user, "请先登录喵~");
            return;
        }
        ChatProtocol protocol = server.getGson().fromJson(protocolData, ChatProtocol.class); // 反序列化 json 到 object
        if (((ChatRequestImpl) server.getRequest()).userChatRequests(user, protocol.getMessage()))  // 检查是否允许发送消息
            return;
        log.info("[{}]:{}", user.getUserName(), protocol.getMessage());// 打印消息到log

        ChatProtocol chatProtocol = new ChatProtocol();// 封装数据包发给所有用户
        chatProtocol.setSourceUserName(user.getUserName());
        chatProtocol.setMessage(protocol.getMessage());
        String SendProtocolData = server.getGson().toJson(chatProtocol);
        server.getServerAPI().getValidUserList(true).forEach((forEachUser) ->
                server.getServerAPI().sendJsonToClient(forEachUser, SendProtocolData, "ChatProtocol"));
    }
}
