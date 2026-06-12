package org.yuezhikong.Client;

import lombok.extern.slf4j.Slf4j;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.yuezhikong.Main;

@Slf4j
public class ClientMain {
    public void start(){
        Terminal terminal = Main.getTerminal();
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
        while (true) {
            String line = reader.readLine(">").trim();
            log.info(line);
        }
    }
}
