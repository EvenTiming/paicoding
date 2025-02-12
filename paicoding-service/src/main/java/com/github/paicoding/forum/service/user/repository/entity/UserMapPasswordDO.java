package com.github.paicoding.forum.service.user.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.paicoding.forum.api.model.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("user_map_password")
public class UserMapPasswordDO {

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 用户纬度
     */
    private double lat;

    /**
     * 用户经度
     */
    private double lng;
}
