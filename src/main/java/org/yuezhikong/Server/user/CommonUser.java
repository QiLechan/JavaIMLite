package org.yuezhikong.Server.user;

import org.yuezhikong.Server.Server;

public abstract class CommonUser implements User {
    private userInformation userInformation;
    private UserAuthentication authentication;

    @Override
    public String getUserName() {
        if (userInformation != null)
            return authentication.getUserName();
        return "";
    }

    @Override
    public boolean isUserLogged() {
        if (authentication != null) {
            return authentication.isLogin();
        }
        return false;
    }

    @Override
    public User disconnect() {
        Server.getInstance().disconnectUser(this);
        return this;
    }

    /**
     * 设置用户Authentication实例
     *
     * @param authentication 实例
     */
    @Override
    public User setUserAuthentication(UserAuthentication authentication) {
        this.authentication = authentication;
        return this;
    }

    /**
     * 获取用户Authentication实例
     *
     * @return Authentication实例
     */
    @Override
    public UserAuthentication getUserAuthentication() {
        return authentication;
    }

    /**
     * 设置用户数据库信息
     *
     * @param userInformation 用户数据库信息
     */
    @Override
    public void setUserInformation(userInformation userInformation) {
        this.userInformation = userInformation;
    }

    /**
     * 获取用户数据库信息
     *
     * @return 用户数据库信息
     * @apiNote 操作后请使用setUserInformation重新写入，操作后只有通过此方法才会持久化保存
     */
    @Override
    public userInformation getUserInformation() {
        return userInformation;
    }
}
