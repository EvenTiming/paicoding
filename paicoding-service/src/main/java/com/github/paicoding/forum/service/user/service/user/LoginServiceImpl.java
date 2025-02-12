package com.github.paicoding.forum.service.user.service.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.paicoding.forum.api.model.context.ReqInfoContext;
import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.api.model.vo.user.UserPwdLoginReq;
import com.github.paicoding.forum.api.model.vo.user.UserSaveReq;
import com.github.paicoding.forum.service.user.repository.dao.UserAiDao;
import com.github.paicoding.forum.service.user.repository.dao.UserDao;
import com.github.paicoding.forum.service.user.repository.dao.UserMapPasswordDao;
import com.github.paicoding.forum.service.user.repository.entity.UserAiDO;
import com.github.paicoding.forum.service.user.repository.entity.UserDO;
import com.github.paicoding.forum.service.user.repository.entity.UserMapPasswordDO;
import com.github.paicoding.forum.service.user.service.LoginService;
import com.github.paicoding.forum.service.user.service.RegisterService;
import com.github.paicoding.forum.service.user.service.UserAiService;
import com.github.paicoding.forum.service.user.service.UserService;
import com.github.paicoding.forum.service.user.service.help.StarNumberHelper;
import com.github.paicoding.forum.service.user.service.help.UserPwdEncoder;
import com.github.paicoding.forum.service.user.service.help.UserSessionHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于验证码、用户名密码的登录方式
 *
 * @author YiHui
 * @date 2022/8/15
 */
@Service
@Slf4j
public class LoginServiceImpl implements LoginService {
    @Autowired
    private UserDao userDao;

    @Autowired
    private UserAiDao userAiDao;

    @Autowired
    private UserMapPasswordDao userMapPasswordDao;

    @Autowired
    private UserSessionHelper userSessionHelper;
    @Autowired
    private StarNumberHelper starNumberHelper;

    @Autowired
    private RegisterService registerService;

    @Autowired
    private UserPwdEncoder userPwdEncoder;

    @Autowired
    private UserService userService;

    @Autowired
    private UserAiService userAiService;

    @Autowired
    private PasswordEncoder passwordEncoder;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long autoRegisterWxUserInfo(String uuid) {
        UserSaveReq req = new UserSaveReq().setLoginType(0).setThirdAccountId(uuid);
        Long userId = registerOrGetUserInfo(req);
        ReqInfoContext.getReqInfo().setUserId(userId);
        return userId;
    }

    /**
     * 没有注册时，先注册一个用户；若已经有，则登录
     *
     * @param req
     */
    private Long registerOrGetUserInfo(UserSaveReq req) {
        UserDO user = userDao.getByThirdAccountId(req.getThirdAccountId());
        if (user == null) {
            return registerService.registerByWechat(req.getThirdAccountId());
        }
        return user.getId();
    }

    @Override
    public void logout(String session) {
        userSessionHelper.removeSession(session);
    }

    /**
     * 给微信公众号的用户生成一个用于登录的会话
     *
     * @param userId 用户id
     * @return
     */
    @Override
    public String loginByWx(Long userId) {
        return userSessionHelper.genSession(userId);
    }

    /**
     * 用户名密码方式登录
     *
     * @param username 用户名
     * @param password 密码
     * @return
     */
    @Override
    public String loginByUserPwd(String username, String password) {
        UserDO user = userDao.getUserByUserName(username);
        if (user == null) {
            throw ExceptionUtil.of(StatusEnum.USER_NOT_EXISTS, "userName=" + username);
        }

        // passwordEncoder.matches(password, user.getPassword());

        if (!userPwdEncoder.match(password, user.getPassword())) {
            throw ExceptionUtil.of(StatusEnum.USER_PWD_ERROR);
        }

        Long userId = user.getId();
        // 1. 为了兼容历史数据，对于首次登录成功的用户，初始化ai信息
        userAiService.initOrUpdateAiInfo(new UserPwdLoginReq().setUserId(userId).setUsername(username).setPassword(password));

        // 登录成功，返回对应的session
        ReqInfoContext.getReqInfo().setUserId(userId);
        return userSessionHelper.genSession(userId);
    }

    /**
     *
     * @param username 用户名
     * @param lat 经度
     * @param lng 维度
     * @return
     */
    @Override
    public String loginByUserMap(String username, double lat, double lng) {
        UserDO user = userDao.getUserByUserName(username);
        if (user == null) {
            throw ExceptionUtil.of(StatusEnum.USER_NOT_EXISTS, "userName=" + username);
        }
        Long userid = user.getId();
        QueryWrapper<UserMapPasswordDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userid);
        UserMapPasswordDO userMapPasswordDO = userMapPasswordDao.getOne(queryWrapper);
        if (userMapPasswordDO == null) {
            throw ExceptionUtil.of(StatusEnum.USER_NOT_EXISTS, "userName=" + username);
        }
        if (haversine(lat, lng, userMapPasswordDO.getLat(), userMapPasswordDO.getLng()) > 0.2) {
            throw ExceptionUtil.of(StatusEnum.USER_PWD_ERROR);
        }
        Long userId = user.getId();
        // 登录成功，返回对应的session
        ReqInfoContext.getReqInfo().setUserId(userId);
        return userSessionHelper.genSession(userId);
    }

    @Override
    public boolean registerByMap(double lat, double lng) {
        Long userid = ReqInfoContext.getReqInfo().getUserId();
        return userMapPasswordDao.saveOrUpdateUser(userid, lat, lng);
    }

    /**
     * 用户名密码方式登录，若用户不存在，则进行注册
     *
     * @param loginReq 登录信息
     * @return
     */
    @Override
    public String registerByUserPwd(UserPwdLoginReq loginReq) {
        // 1. 前置校验
        registerPreCheck(loginReq);

        // 2. 判断当前用户是否登录，若已经登录，则直接走绑定流程
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        loginReq.setUserId(userId);
        if (userId != null) {
            // 2.1 如果用户已经登录，则走绑定用户信息流程
            userService.bindUserInfo(loginReq);
            return ReqInfoContext.getReqInfo().getSession();
        }


        // 3. 尝试使用用户名进行登录，若成功，则依然走绑定流程
        UserDO user = userDao.getUserByUserName(loginReq.getUsername());
        if (user != null) {
            if (!userPwdEncoder.match(loginReq.getPassword(), user.getPassword())) {
                // 3.1 用户名已经存在
                throw ExceptionUtil.of(StatusEnum.USER_LOGIN_NAME_REPEAT, loginReq.getUsername());
            }

            // 3.2 用户存在，尝试走绑定流程
            userId = user.getId();
            loginReq.setUserId(userId);
            userAiService.initOrUpdateAiInfo(loginReq);
        } else {
            //4. 走用户注册流程
            userId = registerService.registerByUserNameAndPassword(loginReq);
        }
        ReqInfoContext.getReqInfo().setUserId(userId);
        return userSessionHelper.genSession(userId);
    }


    /**
     * 注册前置校验
     *
     * @param loginReq
     */
    private void registerPreCheck(UserPwdLoginReq loginReq) {
        if (StringUtils.isBlank(loginReq.getUsername()) || StringUtils.isBlank(loginReq.getPassword())) {
            throw ExceptionUtil.of(StatusEnum.USER_PWD_ERROR);
        }

        String starNumber = loginReq.getStarNumber();
        // 若传了星球信息，首先进行校验
        if (StringUtils.isNotBlank(starNumber)) {
            if (Boolean.FALSE.equals(starNumberHelper.checkStarNumber(starNumber))) {
                // 星球编号校验不通过，直接抛异常
                throw ExceptionUtil.of(StatusEnum.USER_STAR_NOT_EXISTS, "星球编号=" + starNumber);
            }

            UserAiDO userAi = userAiDao.getByStarNumber(starNumber);

            // 如果星球编号已经被绑定了
            if (userAi != null) {
                // 判断星球是否已经被绑定了
                throw ExceptionUtil.of(StatusEnum.USER_STAR_REPEAT, starNumber);
            }
        }

        String invitationCode = loginReq.getInvitationCode();
        if (StringUtils.isNotBlank(invitationCode) && userAiDao.getByInviteCode(invitationCode) == null) {
            // 填写的邀请码不对, 找不到对应的用户
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, "非法的邀请码【" + starNumber + "】");
        }
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        // 地球半径（单位：公里）
        double R = 6371.0;

        // 将经纬度从度转换为弧度
        lat1 = Math.toRadians(lat1);
        lon1 = Math.toRadians(lon1);
        lat2 = Math.toRadians(lat2);
        lon2 = Math.toRadians(lon2);

        // 计算经纬度差
        double dlat = lat2 - lat1;
        double dlon = lon2 - lon1;

        // 哈弗辛公式
        double a = Math.pow(Math.sin(dlat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dlon / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // 计算距离
        return R * c;
    }
}
