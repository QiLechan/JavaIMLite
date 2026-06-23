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
import org.yuezhikong.Server.protocol.TransferProtocol;
import org.yuezhikong.Server.user.User;

import java.io.File;
import java.util.List;
import java.util.UUID;

/**
 * 文件上传命令
 */
@Slf4j
public class UploadCommand implements Command {
    
    @Override
    public boolean execute(String command, String[] args, User user) {
        if (args.length != 1) {
            return false;
        }
        
        Server serverInstance = Server.getInstance();
        ServerAPI serverAPI = serverInstance.getServerAPI();
        
        String filePath = args[0];
        File file = new File(filePath);
        
        // 检查文件是否存在
        if (!file.exists()) {
            serverAPI.sendMessageToUser(user, "文件不存在: " + filePath);
            return true;
        }
        
        // 检查是否是文件
        if (!file.isFile()) {
            serverAPI.sendMessageToUser(user, "路径不是一个文件: " + filePath);
            return true;
        }
        
        // 检查文件大小（限制为10MB）
        long fileSize = file.length();
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (fileSize > maxSize) {
            serverAPI.sendMessageToUser(user, "文件太大，最大支持10MB");
            return true;
        }
        
        try {
            // 读取文件内容并编码为Base64
            byte[] fileContent = java.nio.file.Files.readAllBytes(file.toPath());
            File uploadDirectory = new File("./uploads/");
            if (!uploadDirectory.exists() && !uploadDirectory.mkdirs()) {
                throw new IllegalStateException("Failed to create uploads directory");
            }
            String storedFileName = UUID.randomUUID() + "_" + file.getName();
            java.nio.file.Files.write(new File(uploadDirectory, storedFileName).toPath(), fileContent);
            
            // 创建文件传输请求
            FileTransferRequestHandler handler = FileTransferRequestHandler.getInstance();
            String requestId = handler.createRequest(file.getName(), storedFileName, fileSize, user);
            
            // 构建TransferProtocol广播给所有用户
            TransferProtocol transferProtocol = new TransferProtocol();
            TransferProtocol.TransferProtocolHeadBean headBean = new TransferProtocol.TransferProtocolHeadBean();
            headBean.setType("upload_request");
            headBean.setTargetUserName(""); // 广播给所有用户
            
            TransferProtocol.TransferProtocolBodyBean fileNameBean = new TransferProtocol.TransferProtocolBodyBean();
            fileNameBean.setData(file.getName());
            
            TransferProtocol.TransferProtocolBodyBean fileSizeBean = new TransferProtocol.TransferProtocolBodyBean();
            fileSizeBean.setData(String.valueOf(fileSize));
            
            TransferProtocol.TransferProtocolBodyBean requestIdBean = new TransferProtocol.TransferProtocolBodyBean();
            requestIdBean.setData(requestId);
            
            TransferProtocol.TransferProtocolBodyBean senderBean = new TransferProtocol.TransferProtocolBodyBean();
            senderBean.setData(user.getUserName());
            
            transferProtocol.setTransferProtocolHead(headBean);
            transferProtocol.setTransferProtocolBody(List.of(fileNameBean, fileSizeBean, requestIdBean, senderBean));
            
            String protocolData = serverInstance.getGson().toJson(transferProtocol);
            
            // 广播给所有在线用户
            List<User> onlineUsers = serverAPI.getValidUserList(true);
            for (User onlineUser : onlineUsers) {
                if (!onlineUser.equals(user)) { // 不发送给发送者自己
                    serverAPI.sendJsonToClient(onlineUser, protocolData, "TransferProtocol");
                }
            }
            
            serverAPI.sendMessageToUser(user, "文件上传请求已广播，等待其他用户响应...");
            log.info("用户 {} 发起文件上传请求: {} ({} bytes)", user.getUserName(), file.getName(), fileSize);
            
        } catch (Exception e) {
            log.error("文件上传失败", e);
            serverAPI.sendMessageToUser(user, "文件上传失败: " + e.getMessage());
        }
        
        return true;
    }
    
    @Override
    public String getDescription() {
        return "上传文件到服务器并广播给所有用户";
    }
    
    @Override
    public String getUsage() {
        return "/upload <文件路径>";
    }
    
    @Override
    public boolean isAllowBroadcastCommandRunning() {
        return true;
    }
}
