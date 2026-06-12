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

package org.yuezhikong.utils.database.dao;

import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.Nullable;
import org.yuezhikong.Server.user.userInformation;

import java.util.List;

public interface userInformationDao {
    /**
     * 从数据库获取用户列表
     *
     * @return 用户数据库信息实体类
     */
    @Nullable
    List<userInformation> getUserList();

    /**
     * 获取用户
     *
     * @param userId   用户Id
     * @param userName 用户名
     * @param token    Token
     * @param salt     Salt
     * @return 用户数据库信息实体类
     * @apiNote 只需要有一个条件即可查询, 无需全部满足
     */
    @Nullable
    userInformation getUser(@Nullable @Param("userId") String userId,
                            @Nullable @Param("userName") String userName,
                            @Nullable @Param("token") String token,
                            @Nullable @Param("salt") String salt
    );

    /**
     * 根据用户名从数据库中获取用户
     *
     * @param userName 用户名
     * @return 用户数据库信息实体类
     */
    @Nullable
    userInformation getUserByName(String userName);

    /**
     * 根据Token从数据库中获取用户
     *
     * @param token Token
     * @return 用户数据库信息实体类
     */
    @Nullable
    userInformation getUserByToken(String token);

    /**
     * 根据密码盐从数据库中获取用户
     *
     * @param salt 盐
     * @return 用户数据库信息实体类
     */
    @Nullable
    userInformation getUserBySalt(String salt);

    /**
     * 向数据库添加一个用户
     *
     * @param User 用户
     * @return 操作是否成功
     */
    Boolean addUser(userInformation User);

    /**
     * 更新数据库保存的用户信息
     *
     * @param User 用户
     * @return 操作是否成功
     */
    Boolean updateUser(userInformation User);
}
