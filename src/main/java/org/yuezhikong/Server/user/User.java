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

package org.yuezhikong.Server.user;

public interface User {

    /**
     * 获取用户名
     *
     * @return 用户名
     */
    String getUserName();

    /**
     * 获取用户登录状态
     *
     * @return {@code true} 已登录, {@code false} 未登录
     */
    boolean isUserLogged();

    /**
     * 使用户离线（踢出用户）
     */
    User disconnect();

    /**
     * 获取此用户是/否是服务端虚拟用户
     *
     * @return {@code true} 是服务端虚拟账户 {@code false} 不是服务端虚拟账户
     */
    boolean isServer();

    /**
     * 设置用户Authentication实例
     *
     * @param Authentication 实例
     */
    User setUserAuthentication(UserAuthentication Authentication);

    /**
     * 获取用户Authentication实例
     *
     * @return Authentication实例
     */
    UserAuthentication getUserAuthentication();

    /**
     * 设置用户数据库信息
     *
     * @param userInformation 用户数据库信息
     */
    void setUserInformation(userInformation userInformation);

    /**
     * 获取用户数据库信息
     *
     * @return 用户数据库信息
     * @apiNote 操作后请使用setUserInformation重新写入，操作后只有通过此方法才会持久化保存
     */
    userInformation getUserInformation();

    /**
     * 当用户登录时
     *
     * @param UserName 用户名
     * @apiNote 在用户登录时，请调用此方法
     */
    User onUserLogin(String UserName);
}
