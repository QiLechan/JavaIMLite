package org.yuezhikong.Server.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class userInformation {
    private String UserName;
    private String Passwd;
    private String salt;
    private String userId;
}
