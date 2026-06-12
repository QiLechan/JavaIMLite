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
