package com.sky.mapper;

import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper {
    /**
     * 插入订单数据
     * @param order
     */
    void insert(Orders order);
    /**
     * 根据订单号和用户id查询订单
     * @param orderNumber
     * @param userId
     */
    @Select("select * from orders where number = #{orderNumber} and user_id= #{userId}")
    Orders getByNumberAndUserId(String orderNumber, Long userId);
    /**
     * 历史订单分页查询
     * @param orders 查询条件
     * @return 订单列表
     */
    List<Orders> pageQuery(Orders orders);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);



    /**
     * 根据订单ID和用户ID查询订单
     */
    @Select("select * from orders where id = #{id} and user_id = #{userId}")
    Orders getByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId
    );
    /**
     * 根据状态和下单时间查询订单
     * @param status
     * @param orderTime
     */
    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> getByStatusAndOrdertimeLT(Integer status, LocalDateTime orderTime);


    /**
     * 根据id查询订单
     * @param id
     */
    @Select("select * from orders where id=#{id}")
    Orders getById(Long id);
}