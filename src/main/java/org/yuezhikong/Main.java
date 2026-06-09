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
package org.yuezhikong;

import lombok.Getter;
import org.jline.jansi.AnsiConsole;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.yuezhikong.Server.Server;
import org.yuezhikong.utils.ConfigFileManager;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;

public class Main {
    private final static Logger log;
    @Getter
    private final static Terminal terminal;

    static {
        System.out.println("正在初始化JavaIM...");
        System.out.println("正在初始化Slf4j...");
        // Slf4j Logger加载
        log = LoggerFactory.getLogger(Main.class);
        // 安装 JUL to slf4j
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        if (log.isTraceEnabled())
            java.util.logging.Logger.getLogger("").setLevel(Level.FINEST);
        // 初始化 JLine Terminal
        log.info("正在初始化Jline...");
        Terminal terminal1;
        try {
            if (System.console() != null) {
                AnsiConsole.systemInstall();
                terminal1 = AnsiConsole.getTerminal();
            } else
                terminal1 = TerminalBuilder.builder().system(true).exec(false).ffm(false).jna(false).dumb(true).build();
        } catch (IOException e) {
            terminal1 = null;
            log.error("JavaIM 初始化失败");
            System.exit(1);
        }
        terminal = terminal1;
        log.info("JavaIM初始化完成");
    }

    public static void main(String[] args) {
        int serverPort;
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();

        log.info("欢迎使用JavaIm!");
        log.info("正在启动服务端...");
        // 服务端配置文件初始化
        if (!(new File("server.properties").exists())) {
            log.info("目录下没有检测到服务端配置文件，进入配置引导");
            ConfigFileManager.createServerConfig();
            firstStart(reader);
        } else
            ConfigFileManager.reloadServerConfig();
        serverPort = Integer.parseInt(ConfigFileManager.getServerConfig("serverPort", "8080"));

        // 服务端线程组，便于统一停止
        ThreadGroup serverGroup = new ThreadGroup(Thread.currentThread().getThreadGroup(), "serverGroup");
        try {
            // 定义全局的 server 变量，方便在异常或退出时进行 stop 释放
            final Server server = new Server();

            Thread t = new Thread(serverGroup, "ServerThread") {
                @Override
                public void run() {
                    log.info("核心服务线程已唤醒，正在绑定端口并发起网络监听...");
                    // 完美对应我们修改后的 Server.java 启动方法
                    server.start(serverPort);
                }
            };
            t.start();
            t.join(); // 主线程挂起等待，直到业务线程结束
        } catch (InterruptedException e) {
            log.error("出现错误!", e);
        }
        System.exit(0);
    }

    /**
     * 首次启动 JavaIM 时的向导
     *
     * @param reader LineReader
     */
    private static void firstStart(LineReader reader) {
        log.info("检测到您可能是首次启动 JavaIM, 是否进行配置?(Y/N)");
        if (!"Y".equals(reader.readLine(">").toUpperCase(Locale.ROOT)))
            return;
        log.info("请设置服务器名称");
        ConfigFileManager.setServerConfig("serverName", reader.readLine("服务器名称>"));
        ConfigFileManager.setServerConfig("sqlite", "true");
        ConfigFileManager.setServerConfig("serverPort", reader.readLine("服务器端口>"));
        log.info("正在保存您的配置...");
        ConfigFileManager.saveServerConfig();
        log.info("设置向导成功完成!");
        log.info("正在保存您的配置...");
        ConfigFileManager.saveServerConfig();
        log.info("设置向导成功完成!");
    }
}
