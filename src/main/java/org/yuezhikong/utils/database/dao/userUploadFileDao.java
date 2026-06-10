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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yuezhikong.Server.userData.userUploadFile;

import java.util.List;

public interface userUploadFileDao {
    /**
     * 获取用户上传的文件
     *
     * @param fileId 文件Id
     * @return 用户拥有的文件的数据库信息实体类
     */
    @Nullable
    @Contract(pure = true)
    userUploadFile getUploadFileByFileId(@NotNull @Param("ownFile") String fileId);

    /**
     * 获取上传文件的列表
     *
     * @return 用户拥有的文件的数据库信息实体类
     */
    @Nullable
    @Contract(pure = true)
    List<userUploadFile> getUploadFiles();

    /**
     * 获取用户上传的文件
     *
     * @param userId 用户Id
     * @return 用户拥有的文件的数据库信息实体类
     */
    @Nullable
    @Contract(pure = true)
    List<userUploadFile> getUploadFilesByUserId(@NotNull @Param("userId") String userId);

    /**
     * 向数据库添加一个文件信息
     *
     * @param file 文件
     * @return 操作是否成功
     */
    Boolean addFile(userUploadFile file);

    /**
     * 更新数据库保存的文件信息
     *
     * @param file 文件
     * @return 操作是否成功
     */
    Boolean updateFile(userUploadFile file);

    /**
     * 更新数据库保存的文件信息
     *
     * @param file 文件
     * @return 操作是否成功
     */
    Boolean deleteFile(userUploadFile file);
}
