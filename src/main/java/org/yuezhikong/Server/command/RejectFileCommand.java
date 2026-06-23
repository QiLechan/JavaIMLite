package org.yuezhikong.Server.command;

import lombok.extern.slf4j.Slf4j;
import org.yuezhikong.Server.Server;
import org.yuezhikong.Server.ServerAPI;
import org.yuezhikong.Server.filetransfer.FileTransferRequestHandler;
import org.yuezhikong.Server.user.User;

@Slf4j
public class RejectFileCommand implements Command {

    @Override
    public boolean execute(String command, String[] args, User user) {
        if (args.length != 1) {
            return false;
        }

        ServerAPI serverAPI = Server.getInstance().getServerAPI();

        String requestId = args[0];
        FileTransferRequestHandler.FileTransferRequest request =
                FileTransferRequestHandler.getInstance().getRequest(requestId);

        if (request == null) {
            serverAPI.sendMessageToUser(user, "File transfer request does not exist or has expired");
            return true;
        }

        serverAPI.sendMessageToUser(request.getSender(),
                String.format("User %s rejected your file: %s", user.getUserName(), request.getFileName()));
       serverAPI.sendMessageToUser(user,
               String.format("You rejected file: %s", request.getFileName()));

      log.info("User {} rejected file {} from {}", user.getUserName(),
              request.getFileName(), request.getSender().getUserName());

      return true;
    }

    @Override
    public String getDescription() {
        return "Reject file transfer";
    }

    @Override
    public String getUsage() {
        return "/n <requestId>";
    }

    @Override
    public boolean isAllowBroadcastCommandRunning() {
        return false;
    }
}
