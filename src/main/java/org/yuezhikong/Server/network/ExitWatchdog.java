package org.yuezhikong.Server.network;

import lombok.Getter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ExitWatchdog {
    @Getter
    private static ExitWatchdog instance;
    private static final AtomicBoolean Exited = new AtomicBoolean(false);
    private static final Lock lock = new ReentrantLock();

    /**
     * 单例模式
     */
    private ExitWatchdog() {}

    /**
     * 初始化实例
     */
    public static void initInstance() {
        instance = new ExitWatchdog();
        instance.Daemon();
    }

    /**
     * 守护进程
     */
    private void Daemon() {
        while (true) {
            lock.lock();
            try {
                if (Exited.get()) {
                    return;
                }
            } catch (Throwable t) {
                synchronized (this) {
                    lock.unlock();
                    try {
                        this.wait();
                    } catch (InterruptedException ignored) {}
                }
                continue;
            }
            lock.unlock();
        }
    }

    /**
     * 当JavaIM退出时调用
     */
    public void onExit() {
        lock.lock();
        Exited.set(true);
        synchronized (this) {
            notifyAll();
        }
        lock.unlock();
    }
}
