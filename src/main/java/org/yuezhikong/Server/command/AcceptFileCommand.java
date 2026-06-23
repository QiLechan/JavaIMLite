package org.yuezhikong.Server.command;

import lombok.extern.slf4j.Slf4j;
import org.yuezhikong.Server.Server;
import org.yuezhikong.Server.ServerAPI;
import org.yuezhikong.Server.filetransfer.FileTransferRequestHandler;
import org.yuezhikong.Server.protocol.TransferProtocol;
import org.yuezhikong.Server.user.User;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;

@Slf4j
public class AcceptFileCommand implements Command {

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
           serverAPI.sendMessageToUser(user, "File transfer request does not exist or has expired");
           return true;
       }

        // Prevent server user from accepting their own upload
        if (request.getSender().equals(user)) {
            serverAPI.sendMessageToUser(user, "You cannot accept your own upload");
            return true;
        }

       try {
           File file = request.getStoredFile();
           if (!file.exists()) {
                serverAPI.sendMessageToUser(user, "File does not exist: " + file.getAbsolutePath());
                handler.removeRequest(requestId);
                return true;
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

            String protocolData = serverInstance.getGson().toJson(transferProtocol);
            serverAPI.sendJsonToClient(user, protocolData, "TransferProtocol");

            serverAPI.sendMessageToUser(request.getSender(),
                    String.format("User %s accepted your file: %s", user.getUserName(), request.getFileName()));
            serverAPI.sendMessageToUser(user,
                    String.format("Receiving file: %s (%d bytes)", request.getFileName(), request.getFileSize()));

          log.info("User {} accepted file {} from {}", user.getUserName(),
                  request.getFileName(), request.getSender().getUserName());

      } catch (Exception e) {
           log.error("File transfer failed", e);
            serverAPI.sendMessageToUser(user, "File transfer failed: " + e.getMessage());
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "Accept file transfer";
    }

    @Override
    public String getUsage() {
        return "/y <requestId>";
    }

    @Override
    public boolean isAllowBroadcastCommandRunning() {
        return false;
    }
}
