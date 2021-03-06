
/**
 * Tencent is pleased to support the open source community by making MSEC available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the GNU General Public License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. You may 
 * obtain a copy of the License at
 *
 *     https://opensource.org/licenses/GPL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the 
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */


package beans.service;

import beans.dbaccess.StaffInfo;
import beans.request.LoginRequest;

import beans.response.LoginResponse;
import msec.org.*;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 * 用户登录
 */
public class ChangePassword extends JsonRPCHandler {


    static public  boolean  checkTicket(String userName, String ticket)
    {
        if (ticket.length() != 96) { return false;}

        JsTea tea = new JsTea(null);
        String s = tea.decrypt(ticket, Login.userTicketKey);
        if (s.length() != 48) { return false;}
        String userNameMd5 = s.substring(0, 32);
        String dt = s.substring(32, 48);
        long ticketInitTime = new Integer(dt.trim()).intValue();
        long currentTime = new Date().getTime()/1000;
        if (ticketInitTime < currentTime && (currentTime-ticketInitTime) > (3600*24))
        {
            return false;
        }
        String md5Str = Tools.md5(userName);
        if (md5Str.equals(userNameMd5))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

     public JsonRPCResponseBase exec(LoginRequest request)
     {
         Logger logger = Logger.getLogger(ChangePassword.class);
         JsonRPCResponseBase resp = new JsonRPCResponseBase();

         if (request.getStaff_name() == null && request.getTgt() == null||
                 request.getNew_password() == null)
         {
             resp.setStatus(100);
             resp.setMessage("login name /password empty!");
             return resp;
         }

         String result = checkIdentity();
         if (!result.equals("success"))
         {
             resp.setStatus(99);
             resp.setMessage(result);
             return resp;
         }

         DBUtil util = new DBUtil();
         if (util.getConnection() == null)
         {
             resp.setStatus(100);
             resp.setMessage("db connect failed!");
             return resp;
         }

         List<StaffInfo> staffInfoList ;
         try {
             String sql = "select staff_name, staff_phone,password,salt from t_staff "+
                     " where  staff_name=? ";
             List<Object> params = new ArrayList<Object>();
             params.add(request.getStaff_name());

             staffInfoList = util.findMoreRefResult(sql, params, StaffInfo.class);
             if (staffInfoList.size() != 1)
             {
                 resp.setMessage("user does NOT exist.");
                 resp.setStatus(100);
                 return resp;
             }
             //用加盐的二次密码hash作为key（数据库里存着）解密
             StaffInfo staffInfo = staffInfoList.get(0);
             JsTea tea = new JsTea(this.getServlet());
             String p1 = tea.decrypt(request.getTgt(), staffInfo.getPassword());
             ///获取session里保存的challenge
             String challenge = (String)(getHttpRequest().getSession().getAttribute(GetSalt.CHALLENGE_KEY_IN_SESSION));
             if (p1.length() != 40 )
             {
                 resp.setMessage("p1 error!");
                 resp.setStatus(100);
                 return resp;
             }
             //看解密处理的后面部分内容是否同challenge，放重放
             if (!p1.substring(32).equals(challenge))
             {
                 resp.setMessage("p1 error!!");
                 resp.setStatus(100);
                 return resp;
             }
            //根据解密出来的一次密码hash，现场生成二次加盐的hash，与数据库里保存的比较看是否相等
             String p2 = AddNewStaff.geneSaltedPwd(p1.substring(0, 32), staffInfo.getSalt());
             if (!p2.equals(staffInfo.getPassword()))
             {
                 resp.setMessage("p1 error!!!");
                 resp.setStatus(100);
                 return resp;
             }
             //当前密码验证成功，开始改密
             sql = "update t_staff set password=? where staff_name=?";
             params = new ArrayList<Object>();
             params.add(request.getNew_password());
             params.add(request.getStaff_name());

             int updateNum = util.updateByPreparedStatement(sql, params);
             if (updateNum != 1)
             {
                 resp.setMessage("update password failed");
                 resp.setStatus(100);
                 return resp;
             }
             resp.setMessage("success");
             resp.setStatus(0);
             return resp;
         }
         catch (Exception e)
         {
             resp.setStatus(100);
             resp.setMessage("db query exception!");
             logger.error(e);
             return resp;
         }
         finally {
             util.releaseConn();
         }
     }
}
