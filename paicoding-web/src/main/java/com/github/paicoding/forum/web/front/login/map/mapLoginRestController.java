package com.github.paicoding.forum.web.front.login.map;

import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.core.permission.Permission;
import com.github.paicoding.forum.core.permission.UserRole;
import com.github.paicoding.forum.core.util.SessionUtil;
import com.github.paicoding.forum.service.user.service.LoginService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;


/**
 * 可容忍距离的登录/登出的入口
 *
 * @author eventime
 * @date 2025/2/12
 */
@RestController
@RequestMapping
public class mapLoginRestController {
    @Autowired
    private LoginService loginService;

    /**
     * 同态加密地图登录
     */
    @GetMapping("/login/map")
    public ResVo<Boolean> login(@RequestParam(name = "username") String username,
                                @RequestParam(name = "lat") double lat,
                                @RequestParam(name = "lng") double lng,
                                HttpServletResponse response) {
        String session = loginService.loginByUserMap(username, lat, lng);
        if (StringUtils.isNotBlank(session)) {
            // cookie中写入用户登录信息，用于身份识别
            response.addCookie(SessionUtil.newCookie(LoginService.SESSION_KEY, session));
            return ResVo.ok(true);
        } else {
            return ResVo.fail(StatusEnum.LOGIN_FAILED_MIXED, "用户名和密码登录异常，请稍后重试");
        }
    }

    /**
     * 地图密码注册
     */
    @Permission(role = UserRole.LOGIN)
    @PostMapping("/register/map")
    public ResVo<Boolean> register( @RequestParam(name = "lat") double lat,
                                    @RequestParam(name = "lng") double lng,
                                    HttpServletResponse response) {
        boolean flag = loginService.registerByMap(lat, lng);
        if (flag) {
            return ResVo.ok(true);
        } else {
            return ResVo.fail(StatusEnum.LOGIN_FAILED_MIXED, "地图密码注册失败");
        }
    }




}
