package org.yuezhikong.Server.protocolHandler.Handlers;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import org.yuezhikong.Server.Server;
import org.yuezhikong.Server.protocol.LoginProtocol;
import org.yuezhikong.Server.protocol.SystemProtocol;
import org.yuezhikong.Server.protocolHandler.ProtocolHandler;
import org.yuezhikong.Server.user.User;
//import org.yuezhikong.Server.userData.user;
//import org.yuezhikong.utils.protocol.LoginProtocol;
//import org.yuezhikong.utils.protocol.SystemProtocol;

import java.util.Objects;

public class LoginProHandler implements ProtocolHandler {
    @Override
    public void handleProtocol(@NotNull Server server, @NotNull String protocolData, User user) {
        if (user.isUserLogged()) {// 判断登录状态
            server.getServerAPI().sendMessageToUser(user, "您已经登录过了喵~");
            SystemProtocol protocol = new SystemProtocol();
            protocol.setType("Login");
            protocol.setMessage("Already Logged");
            String json = new Gson().toJson(protocol);
            server.getServerAPI().sendJsonToClient(user, json, "SystemProtocol");
            return;
        }
        LoginProtocol loginProtocol = server.getGson().fromJson(protocolData, LoginProtocol.class);// 反序列化 json 到 object
        if (loginProtocol.getLoginPacketHead() == null || loginProtocol.getLoginPacketBody() == null) {
            SystemProtocol systemProtocol = new SystemProtocol();
            systemProtocol.setType("Error");
            systemProtocol.setMessage("Invalid Packet");
            server.getServerAPI().sendJsonToClient(user, server.getGson().toJson(systemProtocol), "SystemProtocol");
            return;
        }
        if ("passwd".equals(loginProtocol.getLoginPacketHead().getType())) {
            if (loginProtocol.getLoginPacketBody().getNormalLogin().getUserName() == null ||
                    loginProtocol.getLoginPacketBody().getNormalLogin().getUserName().contains("\n") ||
                    loginProtocol.getLoginPacketBody().getNormalLogin().getUserName().contains("\r")) {
                server.getServerAPI().sendMessageToUser(user, "用户名中出现非法字符");
                user.disconnect();
                return;
            }
            if (!Objects.requireNonNull(user.getUserAuthentication()).
                    doLogin(loginProtocol.getLoginPacketBody().getNormalLogin().getUserName(),
                            loginProtocol.getLoginPacketBody().getNormalLogin().getPasswd())) {
                user.disconnect();
            }
        } else
            user.disconnect();
    }
}