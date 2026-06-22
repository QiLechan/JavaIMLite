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
import org.yuezhikong.Server.command.Command;
import org.yuezhikong.Server.filetransfer.FileTransferRequestHandler;
import org.yuezhikong.Server.protocol.ChatProtocol;
import org.yuezhikong.Server.protocol.TransferProtocol;
import org.yuezhikong.Server.protocolHandler.ProtocolHandler;
import org.yuezhikong.Server.request.ChatRequest;
import org.yuezhikong.Server.user.User;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;

public class ChatProHandler implements ProtocolHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatProHandler.class);


    public void handleProtocol(@NotNull Server server, @NotNull String protocolData, User user) {
        if (!user.isUserLogged()){
            server.getServerAPI().sendMessageToUser(user, "请先登录喵~");
            return;
        }
        ChatProtocol protocol = server.getGson().fromJson(protocolData, ChatProtocol.class); // 反序列化 json 到 object
        
        // 检查是否为指令（以/开头）
        if (protocol.getMessage() != null && protocol.getMessage().startsWith("/")) {
            String message = protocol.getMessage();
            String[] tmp = message.split("\\s+");
            String commandName = tmp[0].substring(1); // 去掉/
            
            // 只允许客户端使用/upload指令
            if ("upload".equals(commandName)) {
                handleClientUpload(server, tmp, user);
                return;
            } else if ("y".equals(commandName) || "n".equals(commandName)) {
                // 处理接受/拒绝文件传输
                handleFileTransferResponse(server, commandName, tmp, user);
                return;
            } else {
                // 其他指令仅允许服务端（控制台）使用
                server.getServerAPI().sendMessageToUser(user, "指令仅允许在服务端控制台使用，客户端不支持此指令");
                log.warn("用户 {} 尝试从客户端执行指令: {}", user.getUserName(), message);
                return;
            }
        }
        
        log.info("[{}]:{}", user.getUserName(), protocol.getMessage());// 打印消息到log

        ChatProtocol chatProtocol = new ChatProtocol();// 封装数据包发给所有用户
        chatProtocol.setSourceUserName(user.getUserName());
        chatProtocol.setMessage(protocol.getMessage());
        String SendProtocolData = server.getGson().toJson(chatProtocol);
        server.getServerAPI().getValidUserList(true).forEach((forEachUser) ->
                server.getServerAPI().sendJsonToClient(forEachUser, SendProtocolData, "ChatProtocol"));
    }
    
    /**
     * 处理客户端的文件上传请求
     */
    private void handleClientUpload(Server server, String[] args, User user) {
        if (args.length < 2) {
            server.getServerAPI().sendMessageToUser(user, "语法错误! 正确的语法为：/upload <文件路径>");
            return;
        }
        
        StringBuilder filePathBuilder = new StringBuilder(args[1]);
        for (int i = 2; i < args.length; i++) {
            filePathBuilder.append(" ").append(args[i]);
        }
        String filePath = filePathBuilder.toString();
        
        File file = new File(filePath);
        
        // 检查文件是否存在
        if (!file.exists()) {
            server.getServerAPI().sendMessageToUser(user, "文件不存在: " + filePath);
            return;
        }
        
        // 检查是否是文件
        if (!file.isFile()) {
            server.getServerAPI().sendMessageToUser(user, "路径不是一个文件: " + filePath);
            return;
        }
        
        // 检查文件大小（限制为10MB）
        long fileSize = file.length();
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (fileSize > maxSize) {
            server.getServerAPI().sendMessageToUser(user, "文件太大，最大支持10MB");
            return;
        }
        
        try {
            // 读取文件内容并编码为Base64
            byte[] fileContent = Files.readAllBytes(file.toPath());
            String base64Content = Base64.getEncoder().encodeToString(fileContent);
            
            // 保存文件到服务器
            String uploadDir = "./uploads/";
            File uploadDirectory = new File(uploadDir);
            if (!uploadDirectory.exists()) {
                uploadDirectory.mkdirs();
            }
            
            File destFile = new File(uploadDir + file.getName());
            Files.write(destFile.toPath(), fileContent);
            
            // 创建文件传输请求
            FileTransferRequestHandler handler = FileTransferRequestHandler.getInstance();
            String requestId = handler.createRequest(file.getName(), fileSize, user);
            
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
            
            String protocolData = server.getGson().toJson(transferProtocol);
            
            // 广播给所有在线用户（包括发送者）
            List<User> onlineUsers = server.getServerAPI().getValidUserList(true);
            for (User onlineUser : onlineUsers) {
                server.getServerAPI().sendJsonToClient(onlineUser, protocolData, "TransferProtocol");
            }
            
            server.getServerAPI().sendMessageToUser(user, "文件上传请求已广播，等待其他用户响应...");
            log.info("用户 {} 发起文件上传请求: {} ({} bytes)", user.getUserName(), file.getName(), fileSize);
            
        } catch (Exception e) {
            log.error("文件上传失败", e);
            server.getServerAPI().sendMessageToUser(user, "文件上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理文件传输响应（接受/拒绝）
     */
    private void handleFileTransferResponse(Server server, String responseType, String[] args, User user) {
        if (args.length < 2) {
            server.getServerAPI().sendMessageToUser(user, "语法错误! 正确的语法为：/" + responseType + " <请求ID>");
            return;
        }
        
        String requestId = args[1];
        FileTransferRequestHandler handler = FileTransferRequestHandler.getInstance();
        FileTransferRequestHandler.FileTransferRequest request = handler.getRequest(requestId);
        
        if (request == null) {
            server.getServerAPI().sendMessageToUser(user, "文件传输请求不存在或已过期");
            return;
        }
        
        if ("y".equals(responseType)) {
            // 接受文件传输
            try {
                // 读取文件内容并编码为Base64
                String filePath = "./uploads/" + request.getFileName();
                File file = new File(filePath);
                
                if (!file.exists()) {
                    server.getServerAPI().sendMessageToUser(user, "文件不存在: " + filePath);
                    handler.removeRequest(requestId);
                    return;
                }
                
                byte[] fileContent = Files.readAllBytes(file.toPath());
                String base64Content = Base64.getEncoder().encodeToString(fileContent);
                
                // 构建下载协议发送给接受者
                TransferProtocol transferProtocol = new TransferProtocol();
                TransferProtocol.TransferProtocolHeadBean headBean = new TransferProtocol.TransferProtocolHeadBean();
                headBean.setType("download");
                headBean.setTargetUserName(user.getUserName());
                
                TransferProtocol.TransferProtocolBodyBean fileNameBean = new TransferProtocol.TransferProtocolBodyBean();
                fileNameBean.setData(request.getFileName());
                
                TransferProtocol.TransferProtocolBodyBean contentBean = new TransferProtocol.TransferProtocolBodyBean();
                contentBean.setData(base64Content);
                
                transferProtocol.setTransferProtocolHead(headBean);
                transferProtocol.setTransferProtocolBody(List.of(fileNameBean, contentBean));
                
                String protocolData = server.getGson().toJson(transferProtocol);
                server.getServerAPI().sendJsonToClient(user, protocolData, "TransferProtocol");
                
                // 通知发送者文件已被接受
                server.getServerAPI().sendMessageToUser(request.getSender(), 
                    String.format("用户 %s 已接受您的文件: %s", user.getUserName(), request.getFileName()));
                
                server.getServerAPI().sendMessageToUser(user, 
                    String.format("正在接收文件: %s (%d bytes)", request.getFileName(), request.getFileSize()));
                
                // 移除请求
                handler.removeRequest(requestId);
                
                log.info("用户 {} 接受了来自 {} 的文件: {}", 
                    user.getUserName(), request.getSender().getUserName(), request.getFileName());
                
            } catch (Exception e) {
                log.error("文件传输失败", e);
                server.getServerAPI().sendMessageToUser(user, "文件传输失败: " + e.getMessage());
            }
        } else if ("n".equals(responseType)) {
            // 拒绝文件传输
            // 通知发送者文件已被拒绝
            server.getServerAPI().sendMessageToUser(request.getSender(), 
                String.format("用户 %s 拒绝了您的文件: %s", user.getUserName(), request.getFileName()));
            
            server.getServerAPI().sendMessageToUser(user, 
                String.format("您已拒绝接收文件: %s", request.getFileName()));
            
            // 移除请求
            handler.removeRequest(requestId);
            
            log.info("用户 {} 拒绝了来自 {} 的文件: {}", 
                user.getUserName(), request.getSender().getUserName(), request.getFileName());
        }
    }
}
