package com.github.paicoding.forum.service.user.repository.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.paicoding.forum.service.user.repository.entity.UserMapPasswordDO;
import com.github.paicoding.forum.service.user.repository.mapper.UserAiHistoryMapper;
import com.github.paicoding.forum.service.user.repository.mapper.UserMapPasswordMapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Repository
public class UserMapPasswordDao extends ServiceImpl<UserMapPasswordMapper, UserMapPasswordDO> {
    @Transactional
    public boolean saveOrUpdateUser(Long userId, double lat, double lng) {
        // 使用MyBatis-Plus的count查询检查是否有该userId的记录
        int count = (int) this.count(new QueryWrapper<UserMapPasswordDO>().eq("user_id", userId));

        if (count == 0) {
            // 如果没有记录，则执行插入操作
            UserMapPasswordDO user = new UserMapPasswordDO();
            user.setUserId(userId);
            user.setLat(lat);
            user.setLng(lng);

            return this.save(user);  // 返回是否保存成功
        } else {
            // 如果有记录，则执行更新操作
            UserMapPasswordDO user = new UserMapPasswordDO();
            user.setLat(lat);
            user.setLng(lng);

            return this.update(new UpdateWrapper<UserMapPasswordDO>()
                    .eq("user_id", userId)  // 查找特定userId的记录
                    .set("lat", lat)  // 更新字段
                    .set("lng", lng));  // 更新其他字段
        }
    }
}
