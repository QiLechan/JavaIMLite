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

package org.yuezhikong.Server.filetransfer;

import lombok.Data;
import org.yuezhikong.Server.user.User;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件传输请求管理器
 * 管理待处理的文件传输请求
 */
public class FileTransferRequestHandler {
    private static volatile FileTransferRequestHandler instance;
    
    /**
     * 文件传输请求信息
     */
    @Data
    public static class FileTransferRequest {
        private String fileName;
        private long fileSize;
        private User sender;
        private long timestamp;
        
        public FileTransferRequest(String fileName, long fileSize, User sender) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.sender = sender;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    // 存储所有待处理的文件传输请求，key为请求ID
    private final Map<String, FileTransferRequest> pendingRequests = new ConcurrentHashMap<>();
    
    private FileTransferRequestHandler() {
    }
    
    /**
     * 获取单例实例
     */
    public static FileTransferRequestHandler getInstance() {
        if (instance == null) {
            synchronized (FileTransferRequestHandler.class) {
                if (instance == null) {
                    instance = new FileTransferRequestHandler();
                }
            }
        }
        return instance;
    }
    
    /**
     * 创建新的文件传输请求
     * 
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param sender 发送者
     * @return 请求ID
     */
    public String createRequest(String fileName, long fileSize, User sender) {
        String requestId = generateRequestId();
        FileTransferRequest request = new FileTransferRequest(fileName, fileSize, sender);
        pendingRequests.put(requestId, request);
        return requestId;
    }
    
    /**
     * 获取文件传输请求
     * 
     * @param requestId 请求ID
     * @return 请求信息，如果不存在返回null
     */
    public FileTransferRequest getRequest(String requestId) {
        return pendingRequests.get(requestId);
    }
    
    /**
     * 移除文件传输请求
     * 
     * @param requestId 请求ID
     */
    public void removeRequest(String requestId) {
        pendingRequests.remove(requestId);
    }
    
    /**
     * 生成唯一的请求ID
     */
    private String generateRequestId() {
        return "file_transfer_" + System.currentTimeMillis() + "_" + Math.random();
    }
    
    /**
     * 清理过期的请求（超过5分钟的请求）
     */
    public void cleanupExpiredRequests() {
        long currentTime = System.currentTimeMillis();
        long expireTime = 5 * 60 * 1000; // 5分钟
        
        pendingRequests.entrySet().removeIf(entry -> 
            (currentTime - entry.getValue().getTimestamp()) > expireTime
        );
    }
}
