package org.yuezhikong.Server.protocolHandler.handlers;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.yuezhikong.Server.Server;
import org.yuezhikong.Server.filetransfer.FileTransferRequestHandler;
import org.yuezhikong.Server.protocol.ChatProtocol;
import org.yuezhikong.Server.protocol.TransferProtocol;
import org.yuezhikong.Server.protocolHandler.ProtocolHandler;
import org.yuezhikong.Server.user.User;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;

@Slf4j
public class ChatProHandler implements ProtocolHandler {
    @Override
    public void handleProtocol(@NotNull Server server, @NotNull String protocolData, User user) {
        if (!user.isUserLogged()) {
            server.getServerAPI().sendMessageToUser(user, "Please login first");
            return;
        }

        ChatProtocol protocol = server.getGson().fromJson(protocolData, ChatProtocol.class);
        if (protocol == null || protocol.getMessage() == null) {
            server.getServerAPI().sendMessageToUser(user, "Invalid chat packet");
            return;
        }

        if (protocol.getMessage().startsWith("/")) {
            String[] tmp = protocol.getMessage().split("\\s+");
            String commandName = tmp[0].substring(1);

            if ("upload".equals(commandName)) {
                server.getServerAPI().sendMessageToUser(user, "Please use TransferProtocol upload");
                return;
            }
            if ("y".equals(commandName) || "n".equals(commandName)) {
                handleFileTransferResponse(server, commandName, tmp, user);
                return;
            }

            server.getServerAPI().sendMessageToUser(user, "This command is only available in the server console");
            log.warn("User {} tried to run unsupported client command: {}", user.getUserName(), protocol.getMessage());
            return;
        }

        log.info("[{}]:{}", user.getUserName(), protocol.getMessage());

        ChatProtocol chatProtocol = new ChatProtocol();
        chatProtocol.setSourceUserName(user.getUserName());
        chatProtocol.setMessage(protocol.getMessage());
        String sendProtocolData = server.getGson().toJson(chatProtocol);
        server.getServerAPI().getValidUserList(true).forEach((forEachUser) ->
                server.getServerAPI().sendJsonToClient(forEachUser, sendProtocolData, "ChatProtocol"));
    }

    private void handleFileTransferResponse(Server server, String responseType, String[] args, User user) {
        if (args.length < 2) {
            server.getServerAPI().sendMessageToUser(user, "Usage: /" + responseType + " <requestId>");
            return;
        }

        String requestId = args[1];
        FileTransferRequestHandler handler = FileTransferRequestHandler.getInstance();
        FileTransferRequestHandler.FileTransferRequest request = handler.getRequest(requestId);

        if (request == null) {
            server.getServerAPI().sendMessageToUser(user, "File transfer request does not exist or has expired");
            return;
        }

        if ("y".equals(responseType)) {
            sendFileToUser(server, handler, requestId, request, user);
            return;
        }

       if ("n".equals(responseType)) {
           server.getServerAPI().sendMessageToUser(request.getSender(),
                   String.format("User %s rejected your file: %s", user.getUserName(), request.getFileName()));
           server.getServerAPI().sendMessageToUser(user,
                   String.format("You rejected file: %s", request.getFileName()));
          log.info("User {} rejected file {} from {}", user.getUserName(),
                  request.getFileName(), request.getSender().getUserName());
      }
    }

   private void sendFileToUser(Server server, FileTransferRequestHandler handler, String requestId,
                               FileTransferRequestHandler.FileTransferRequest request, User user) {
        // Prevent user from accepting their own upload
        if (request.getSender().equals(user)) {
            server.getServerAPI().sendMessageToUser(user, "You cannot accept your own upload");
            return;
        }

       try {
           File file = request.getStoredFile();
           if (!file.exists()) {
                server.getServerAPI().sendMessageToUser(user, "File does not exist: " + file.getAbsolutePath());
                handler.removeRequest(requestId);
                return;
            }

            byte[] fileContent = Files.readAllBytes(file.toPath());
            String base64Content = Base64.getEncoder().encodeToString(fileContent);

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

            server.getServerAPI().sendMessageToUser(request.getSender(),
                    String.format("User %s accepted your file: %s", user.getUserName(), request.getFileName()));
            server.getServerAPI().sendMessageToUser(user,
                    String.format("Receiving file: %s (%d bytes)", request.getFileName(), request.getFileSize()));

          log.info("User {} accepted file {} from {}", user.getUserName(),
                  request.getFileName(), request.getSender().getUserName());

      } catch (Exception e) {
           log.error("File transfer failed", e);
            server.getServerAPI().sendMessageToUser(user, "File transfer failed: " + e.getMessage());
        }
    }
}
