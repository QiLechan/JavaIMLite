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
package org.yuezhikong.Server.request;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.ApiStatus;
import org.yuezhikong.Server.Server;
import org.yuezhikong.Server.command.Command;
import org.yuezhikong.Server.command.Commands;
import org.yuezhikong.Server.user.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class ChatRequest {
    private final Server instance = Server.getInstance();
    
    /**
     * 指令信息
     *
     * @param commandInstance 指令
     * @param command         指令名
     */
    public record CommandInformation(Command commandInstance, String command) {
    }

    private final List<CommandInformation> commands = new ArrayList<>();

    public ChatRequest() {
        // 注册所有内部命令
        registerCommand0(new CommandInformation(new Commands.HelpCommand(), "help"));
        registerCommand0(new CommandInformation(new Commands.ListCommand(), "list"));
        registerCommand0(new CommandInformation(new Commands.TellCommand(), "tell"));
        registerCommand0(new CommandInformation(new Commands.QuitCommand(), "quit"));
        registerCommand0(new CommandInformation(new Commands.ChangePasswordCommand(), "change-password"));
        registerCommand0(new CommandInformation(new Commands.KickCommand(), "kick"));
    }

    /**
     * 指令处理
     *
     * @param command 指令
     * @param args    参数
     * @param user    用户实例
     */
    public void commandRequest(String command, String[] args, User user) {
        try {
            for (CommandInformation information : commands) {
                if (information.command().equals(command)) {
                    if (!information.commandInstance().execute(command, args, user)) {
                        instance.getServerAPI().sendMessageToUser(
                                user,
                                "语法错误! 正确的语法为：" + information.commandInstance().getUsage()
                        );
                        return;
                    }
                    if (information.commandInstance().isAllowBroadcastCommandRunning()) {
                        StringBuilder stringBuilder = new StringBuilder("/").append(command);
                        stringBuilder.append(" ");
                        for (String arg : args) {
                            stringBuilder.append(arg).append(" ");
                        }

                        if (!stringBuilder.isEmpty()) {
                            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                        }
                        String orig_Command = stringBuilder.toString();
                        String tipMessage = String.format("%s 执行了指令: %s", user.getUserName(), orig_Command);
                        log.info(tipMessage);
                        // 只向管理员广播指令执行信息（这里简化处理，只向控制台输出）
                    }
                    return;
                }
            }
            instance.getServerAPI().sendMessageToUser(user, "未知的命令！请输入/help查看帮助！");
        } catch (Throwable t) {
            log.error("在执行{}命令时出现错误!", command, t);
            instance.getServerAPI().sendMessageToUser(user, "在执行此命令的过程中出现未知的错误");
        }
    }

    /**
     * 注册指令（内部方法）
     *
     * @param information 指令信息
     */
    @ApiStatus.Internal
    private void registerCommand0(CommandInformation information) {
        this.commands.add(information);
    }

    /**
     * 获取注册的指令列表
     *
     * @return 指令信息列表
     */
    public List<CommandInformation> getRegisterCommands() {
        return Collections.unmodifiableList(commands);
    }

    /**
     * 注册一条指令
     *
     * @param information 指令信息
     */
    public void registerCommand(CommandInformation information) {
        if (information == null || information.commandInstance() == null) {
            throw new IllegalArgumentException("CommandInformation can not be null");
        }
        for (CommandInformation information1 : commands) {
            if (information1.command().equals(information.command())) {
                throw new IllegalStateException(String.format("Command %s has been already registered", information.command()));
            }
        }
        registerCommand0(information);
    }

    /**
     * 取消注册指令
     *
     * @param information 指令信息
     */
    public void unregisterCommand(CommandInformation information) {
        commands.remove(information);
    }
}
