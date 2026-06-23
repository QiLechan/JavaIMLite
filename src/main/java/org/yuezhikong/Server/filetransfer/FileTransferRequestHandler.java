package org.yuezhikong.Server.filetransfer;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.yuezhikong.Server.user.User;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class FileTransferRequestHandler {
    private static final long REQUEST_EXPIRE_TIME = 5 * 60 * 1000;
    private static final File UPLOAD_DIRECTORY = new File("./uploads/");
    private static volatile FileTransferRequestHandler instance;

    @Data
    public static class FileTransferRequest {
        private String fileName;
        private String storedFileName;
        private long fileSize;
        private User sender;
        private long timestamp;

        public FileTransferRequest(String fileName, String storedFileName, long fileSize, User sender) {
            this.fileName = fileName;
            this.storedFileName = storedFileName;
            this.fileSize = fileSize;
            this.sender = sender;
            this.timestamp = System.currentTimeMillis();
        }

        public File getStoredFile() {
            return new File(UPLOAD_DIRECTORY, storedFileName);
        }
    }

    private final Map<String, FileTransferRequest> pendingRequests = new ConcurrentHashMap<>();

    private FileTransferRequestHandler() {
    }

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

    public String createRequest(String fileName, long fileSize, User sender) {
        return createRequest(fileName, fileName, fileSize, sender);
    }

    public String createRequest(String fileName, String storedFileName, long fileSize, User sender) {
        String requestId = generateRequestId();
        FileTransferRequest request = new FileTransferRequest(fileName, storedFileName, fileSize, sender);
        pendingRequests.put(requestId, request);
        return requestId;
    }

    public FileTransferRequest getRequest(String requestId) {
        return pendingRequests.get(requestId);
    }

    public void removeRequest(String requestId) {
        FileTransferRequest request = pendingRequests.remove(requestId);
        if (request != null) {
            deleteStoredFile(request);
        }
    }

    private String generateRequestId() {
        return "file_transfer_" + System.currentTimeMillis() + "_" + UUID.randomUUID();
    }

    public void cleanupExpiredRequests() {
        long currentTime = System.currentTimeMillis();
        pendingRequests.entrySet().removeIf(entry -> {
            boolean expired = (currentTime - entry.getValue().getTimestamp()) > REQUEST_EXPIRE_TIME;
            if (expired) {
                deleteStoredFile(entry.getValue());
            }
            return expired;
        });
    }

    public void cleanupAllRequests() {
        pendingRequests.values().forEach(this::deleteStoredFile);
        pendingRequests.clear();
    }

    private void deleteStoredFile(FileTransferRequest request) {
        try {
            Files.deleteIfExists(request.getStoredFile().toPath());
        } catch (Exception e) {
            log.warn("Failed to delete upload file: {}", request.getStoredFile().getAbsolutePath(), e);
        }
    }
}
