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

package org.yuezhikong.Server.network;

import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import org.yuezhikong.Server.Server;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class NetworkServer {
    private EventLoopGroup bossGroup, workerGroup;
    private DefaultEventLoopGroup RecvMessageThreadPool;
    public void start(ExecutorService ThreadPool, @Range(from = 1, to = 65535) int serverPort){
        // Java 16 新特性
        record NettyThreadPoolTaskReturn(
                EventLoopGroup bossGroup,
                EventLoopGroup workerGroup,
                DefaultEventLoopGroup RecvMessageThreadPool) {
        }

        Future<?> NettyThreadPoolTask = ThreadPool.submit(() -> {
            log.info("正在创建线程池");
            EventLoopGroup bossGroup = new NioEventLoopGroup(2);
            EventLoopGroup workerGroup = new NioEventLoopGroup(10);
            DefaultEventLoopGroup RecvMessageThreadPool = new DefaultEventLoopGroup(10);
            return new NettyThreadPoolTaskReturn(bossGroup, workerGroup, RecvMessageThreadPool);
        });

        try {
            NettyThreadPoolTaskReturn taskReturn = (NettyThreadPoolTaskReturn) NettyThreadPoolTask.get();
            bossGroup = taskReturn.bossGroup();
            workerGroup = taskReturn.workerGroup();
            RecvMessageThreadPool = taskReturn.RecvMessageThreadPool();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Thread Pool Fatal", e);
        }

        log.info("正在启动Netty");
    }
}
