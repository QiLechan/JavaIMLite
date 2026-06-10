package org.yuezhikong.Server.user;

import org.jetbrains.annotations.Nullable;
import org.yuezhikong.Server.Server;


public class ConsoleUser implements User {

    public ConsoleUser() {
        try {
            Class.forName(new Throwable().getStackTrace()[1].getClassName()).asSubclass(Server.class);
        } catch (ClassCastException | ClassNotFoundException e) {
            throw new UnsupportedOperationException("only Server can create Console User!");
        }
    }

    @Override
    public String getUserName() {
        return "Server";
    }

    @Override
    public User onUserLogin(String UserName) {
        throw new UnsupportedOperationException("Server can not login");
    }

    @Override
    public boolean isUserLogged() {
        return true;
    }

    @Override
    public User disconnect() {
        throw new UnsupportedOperationException("Server can not disconnect");
    }



    @Override
    public boolean isServer() {
        return true;
    }


    @Override
    public User setUserAuthentication(@Nullable UserAuthentication Authentication) {
        throw new UnsupportedOperationException("Server can not use authentication");
    }

    @Override
    public @Nullable UserAuthentication getUserAuthentication() {
        throw new UnsupportedOperationException("Server can not use authentication");
    }

    @Override
    public void setUserInformation(userInformation userInformation) {
        throw new UnsupportedOperationException("Server not in database");
    }

    @Override
    public userInformation getUserInformation() {
        throw new UnsupportedOperationException("server not in database");
    }
}



//    @Override
//    public user setUserPermission(int permissionLevel) {
//        throw new UnsupportedOperationException("Server can not set permission");
//    }

//    @Override
//    public Permission getUserPermission() {
//        return Permission.ADMIN;
//    }
//    @Override
//    public boolean isAllowedTransferProtocol() {
//        return false;
//    }
//
//    @Override
//    public user setAllowedTransferProtocol(boolean allowedTransferProtocol) {
//        throw new UnsupportedOperationException("Server can not set TransferProtocol");
//    }
//
//    @Override
//    public user addLoginRecall(IUserAuthentication.UserRecall code) {
//        throw new UnsupportedOperationException("Server can not login");
//    }
//
//    @Override
//    public user addDisconnectRecall(IUserAuthentication.UserRecall code) {
//        throw new UnsupportedOperationException("Server can not disconnect");
//    }