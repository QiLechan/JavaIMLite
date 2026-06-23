package org.yuezhikong.Server.protocolHandler.handlers;

import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.yuezhikong.Server.Server;
import org.yuezhikong.Server.filetransfer.FileTransferRequestHandler;
import org.yuezhikong.Server.protocol.SystemProtocol;
import org.yuezhikong.Server.protocol.TransferProtocol;
import org.yuezhikong.Server.protocolHandler.ProtocolHandler;
import org.yuezhikong.Server.user.User;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
public class TransferProHandler implements ProtocolHandler {
    private static final long MAX_UPLOAD_SIZE = 10L * 1024L * 1024L;

    @Override
    public void handleProtocol(@NotNull Server server, @NotNull String protocolData, User user) {
        if (!user.isUserLogged()) {
            server.getServerAPI().sendMessageToUser(user, "Please login first");
            return;
        }

        TransferProtocol protocol;
        try {
            protocol = server.getGson().fromJson(protocolData, TransferProtocol.class);
        } catch (JsonSyntaxException e) {
            sendInvalidPacket(server, user);
            return;
        }

        if (protocol == null || protocol.getTransferProtocolHead() == null ||
                protocol.getTransferProtocolHead().getType() == null) {
            sendInvalidPacket(server, user);
            return;
        }

        if ("upload".equals(protocol.getTransferProtocolHead().getType())) {
            handleUpload(server, protocol, user);
            return;
        }

        SystemProtocol systemProtocol = new SystemProtocol();
        systemProtocol.setType("Error");
        systemProtocol.setMessage("Transfer type not support");
        server.getServerAPI().sendJsonToClient(user, server.getGson().toJson(systemProtocol), "SystemProtocol");
    }

    private void handleUpload(Server server, TransferProtocol protocol, User user) {
        List<TransferProtocol.TransferProtocolBodyBean> body = protocol.getTransferProtocolBody();
        if (body == null || body.size() < 2 || body.get(0) == null || body.get(1) == null ||
                body.get(0).getData() == null || body.get(1).getData() == null) {
            sendInvalidPacket(server, user);
            return;
        }

        String fileName = new File(body.get(0).getData()).getName();
        if (fileName.isBlank()) {
            sendInvalidPacket(server, user);
            return;
        }

        byte[] fileContent;
        try {
            fileContent = Base64.getDecoder().decode(body.get(1).getData());
        } catch (IllegalArgumentException e) {
            sendInvalidPacket(server, user);
            return;
        }

        if (fileContent.length > MAX_UPLOAD_SIZE) {
            server.getServerAPI().sendMessageToUser(user, "File too large, max size is 10MB");
            return;
        }

        try {
            File uploadDirectory = new File("./uploads/");
            if (!uploadDirectory.exists() && !uploadDirectory.mkdirs()) {
                throw new IllegalStateException("Failed to create uploads directory");
            }

            String storedFileName = UUID.randomUUID() + "_" + fileName;
            File destFile = new File(uploadDirectory, storedFileName);
            Files.write(destFile.toPath(), fileContent);

            FileTransferRequestHandler handler = FileTransferRequestHandler.getInstance();
            String requestId = handler.createRequest(fileName, storedFileName, fileContent.length, user);

            TransferProtocol uploadRequest = new TransferProtocol();
            TransferProtocol.TransferProtocolHeadBean headBean = new TransferProtocol.TransferProtocolHeadBean();
            headBean.setType("upload_request");
            headBean.setTargetUserName("");

            TransferProtocol.TransferProtocolBodyBean fileNameBean = new TransferProtocol.TransferProtocolBodyBean();
            fileNameBean.setData(fileName);
            TransferProtocol.TransferProtocolBodyBean fileSizeBean = new TransferProtocol.TransferProtocolBodyBean();
            fileSizeBean.setData(String.valueOf(fileContent.length));
            TransferProtocol.TransferProtocolBodyBean requestIdBean = new TransferProtocol.TransferProtocolBodyBean();
            requestIdBean.setData(requestId);
            TransferProtocol.TransferProtocolBodyBean senderBean = new TransferProtocol.TransferProtocolBodyBean();
            senderBean.setData(user.getUserName());

            uploadRequest.setTransferProtocolHead(headBean);
            uploadRequest.setTransferProtocolBody(List.of(fileNameBean, fileSizeBean, requestIdBean, senderBean));

           String protocolData = server.getGson().toJson(uploadRequest);
           for (User onlineUser : server.getServerAPI().getValidUserList(true)) {
                if (onlineUser.equals(user)) {
                    continue;
                }
               server.getServerAPI().sendJsonToClient(onlineUser, protocolData, "TransferProtocol");
           }

            server.getServerAPI().sendMessageToUser(user, "File upload request broadcasted");
            log.info("User {} uploaded file request: {} ({} bytes)", user.getUserName(), fileName, fileContent.length);
        } catch (Exception e) {
            log.error("File upload failed", e);
            server.getServerAPI().sendMessageToUser(user, "File upload failed: " + e.getMessage());
        }
    }

    private void sendInvalidPacket(Server server, User user) {
        SystemProtocol systemProtocol = new SystemProtocol();
        systemProtocol.setType("Error");
        systemProtocol.setMessage("Invalid Packet");
        server.getServerAPI().sendJsonToClient(user, server.getGson().toJson(systemProtocol), "SystemProtocol");
    }
}
