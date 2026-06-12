package org.yuezhikong.Client;

import com.google.gson.Gson;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.yuezhikong.Main;
import org.yuezhikong.Server.protocol.GeneralProtocol;
import org.yuezhikong.Server.protocol.SystemProtocol;

@Slf4j
public class ClientMain {
    protected static final int protocolVersion = 1;
    private final Gson gson = new Gson();

    public void start(String serverAddress, int serverPort, String userName, String password) {
        Terminal terminal = Main.getTerminal();
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
        while (true) {
            String line = reader.readLine(">").trim();
            log.info(line);
            SystemProtocol systemProtocol = new SystemProtocol();
            GeneralProtocol protocol = new GeneralProtocol();
            protocol.setProtocolData(gson.toJson(systemProtocol));
            protocol.setProtocolVersion(protocolVersion);
            sendData(gson.toJson(protocol));
            normalPrint("已发送请求。");
        }
    }

    private void normalPrint(String data) {
        System.out.println(data);
    }

    private void sendData(String Data) {
        channel.writeAndFlush(Data);
    }

    protected Channel channel;
}
