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
import org.yuezhikong.Server.filetransfer.FileTransferRequestHandler;
import org.yuezhikong.Server.user.User;

/**
 * 拒绝文件传输命令 (/n)
 */
@Slf4j
public class RejectFileCommand implements Command {
    
    @Override
    public boolean execute(String command, String[] args, User user) {
        if (args.length != 1) {
            return false;
        }
        
        Server serverInstance = Server.getInstance();
        ServerAPI serverAPI = serverInstance.getServerAPI();
        
        String requestId = args[0];
        FileTransferRequestHandler handler = FileTransferRequestHandler.getInstance();
        FileTransferRequestHandler.FileTransferRequest request = handler.getRequest(requestId);
        
        if (request == null) {
            serverAPI.sendMessageToUser(user, "文件传输请求不存在或已过期");
            return true;
        }
        
        // 通知发送者文件已被拒绝
        serverAPI.sendMessageToUser(request.getSender(), 
            String.format("用户 %s 拒绝了您的文件: %s", user.getUserName(), request.getFileName()));
        
        serverAPI.sendMessageToUser(user, 
            String.format("您已拒绝接收文件: %s", request.getFileName()));
        
        // 移除请求
        handler.removeRequest(requestId);
        
        log.info("用户 {} 拒绝了来自 {} 的文件: {}", 
            user.getUserName(), request.getSender().getUserName(), request.getFileName());
        
        return true;
    }
    
    @Override
    public String getDescription() {
        return "拒绝文件传输";
    }
    
    @Override
    public String getUsage() {
        return "/n <请求ID>";
    }
    
    @Override
    public boolean isAllowBroadcastCommandRunning() {
        return false;
    }
}
