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
}
