package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.OrderVO;


/**
 * 订单
 */
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;


    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //异常情况的处理（收货地址为空、超出配送范围、购物车为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);

        //查询当前用户的购物车数据
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //构造订单数据
        Orders order = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,order);
        order.setPhone(addressBook.getPhone());
        order.setAddress(addressBook.getDetail());
        order.setConsignee(addressBook.getConsignee());
        order.setNumber(String.valueOf(System.currentTimeMillis()));
        order.setUserId(userId);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setPayStatus(Orders.UN_PAID);
        order.setOrderTime(LocalDateTime.now());

        //向订单表插入1条数据
        orderMapper.insert(order);

        //订单明细数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(order.getId());
            orderDetailList.add(orderDetail);
        }

        //向明细表插入n条数据
        orderDetailMapper.insertBatch(orderDetailList);

        //清理购物车中的数据
        shoppingCartMapper.deleteByUserId(userId);

        //封装返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();

        return orderSubmitVO;
    }

    /**
     * 历史订单分页查询
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO) {

        // 1. 开启分页查询
        PageHelper.startPage(
                ordersPageQueryDTO.getPage(),
                ordersPageQueryDTO.getPageSize()
        );

        // 2. 获取当前登录用户ID
        Long userId = BaseContext.getCurrentId();

        // 3. 封装查询条件
        Orders orders = new Orders();
        orders.setUserId(userId);
        orders.setStatus(ordersPageQueryDTO.getStatus());

        // 4. 查询订单
        List<Orders> ordersList = orderMapper.pageQuery(orders);

        // 5. 获取分页结果
        Page<Orders> page = (Page<Orders>) ordersList;
        long total = page.getTotal();

        // 6. 将 Orders 转换为 OrderVO
        List<OrderVO> orderVOList = new ArrayList<>();

        for (Orders order : ordersList) {

            // 查询当前订单对应的商品明细
            List<OrderDetail> orderDetailList =
                    orderDetailMapper.getByOrderId(order.getId());

            // 封装订单VO
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(order, orderVO);

            // 设置订单商品明细
            orderVO.setOrderDetailList(orderDetailList);

            orderVOList.add(orderVO);
        }

        // 7. 返回分页结果
        return new PageResult(total, orderVOList);
    }


    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) {

        // 1. 获取当前登录用户ID
        Long userId = BaseContext.getCurrentId();

        // 2. 获取订单号
        String orderNumber = ordersPaymentDTO.getOrderNumber();

        // 3. 根据订单号和用户ID查询订单
        Orders order = orderMapper.getByNumberAndUserId(orderNumber, userId);

        // 4. 检查订单是否存在，以及是否属于当前用户
        if (order == null) {
            throw new OrderBusinessException("订单不存在或无权支付");
        }

        // 5. 如果订单已经支付，直接返回，避免重复处理
        if (java.util.Objects.equals(order.getPayStatus(), Orders.PAID)) {
            return new OrderPaymentVO();
        }

        // 6. 检查订单是否处于待付款状态
        if (!java.util.Objects.equals(order.getPayStatus(), Orders.UN_PAID)
                || !java.util.Objects.equals(order.getStatus(), Orders.PENDING_PAYMENT)) {
            throw new OrderBusinessException("当前订单状态不允许支付");
        }

        // 7. 构造订单状态更新对象
        Orders updateOrder = Orders.builder()
                .id(order.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        // 8. 更新数据库
        orderMapper.update(updateOrder);
        // 模拟支付成功后，向管理端推送来单提醒
        Map<String, Object> map = new HashMap<>();
        map.put("type", 1);
        map.put("orderId", order.getId());
        map.put("content", "订单号：" + order.getNumber());

        String json = JSON.toJSONString(map);

        log.info("准备向管理端推送来单提醒：{}", json);

        webSocketServer.sendToAllClient(json);

        // 9. 返回支付结果
        return new OrderPaymentVO();
    }


    @Autowired
    private WebSocketServer webSocketServer;
    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();

        // 根据订单号查询当前用户的订单
        Orders ordersDB = orderMapper.getByNumberAndUserId(outTradeNo, userId);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
        //////////////////////////////////////////////
        Map map = new HashMap();
        map.put("type", 1);//消息类型，1表示来单提醒
        map.put("orderId", orders.getId());
        map.put("content", "订单号：" + outTradeNo);

        //通过WebSocket实现来单提醒，向客户端浏览器推送消息
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
        ///////////////////////////////////////////////////
    }
    @Override
    public OrderVO details(Long id) {

        // 获取当前登录用户
        Long userId = BaseContext.getCurrentId();

        // 查询订单
        Orders order = orderMapper.getByIdAndUserId(id, userId);

        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }

        // 查询订单商品明细
        List<OrderDetail> orderDetailList =
                orderDetailMapper.getByOrderId(id);

        // 封装VO
        OrderVO orderVO = new OrderVO();

        BeanUtils.copyProperties(order, orderVO);

        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {

        Long userId = BaseContext.getCurrentId();

        // 查询订单，校验订单归属
        Orders order = orderMapper.getByIdAndUserId(id, userId);

        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }

        // 只有待付款、待接单状态允许取消
        if (!java.util.Objects.equals(
                order.getStatus(), Orders.PENDING_PAYMENT)
                && !java.util.Objects.equals(
                order.getStatus(), Orders.TO_BE_CONFIRMED)) {

            throw new OrderBusinessException("当前订单状态不允许取消");
        }

        // 修改订单状态
        Orders updateOrder = Orders.builder()
                .id(id)
                .status(Orders.CANCELLED)
                .cancelReason("用户取消")
                .cancelTime(LocalDateTime.now())
                .build();

        // 8. 更新数据库
        orderMapper.update(updateOrder);


    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void repetition(Long id) {

        Long userId = BaseContext.getCurrentId();

        // 查询原订单，校验归属
        Orders order = orderMapper.getByIdAndUserId(id, userId);

        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }

        // 查询原订单商品明细
        List<OrderDetail> orderDetailList =
                orderDetailMapper.getByOrderId(id);

        if (orderDetailList == null || orderDetailList.isEmpty()) {
            throw new OrderBusinessException("订单商品不存在");
        }

        // 将原订单商品重新添加到购物车
        for (OrderDetail orderDetail : orderDetailList) {

            ShoppingCart shoppingCart = new ShoppingCart();

            BeanUtils.copyProperties(
                    orderDetail,
                    shoppingCart,
                    "id",
                    "orderId",
                    "createTime"
            );

            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            shoppingCartMapper.insert(shoppingCart);
        }
    }
    /**
     * 用户催单
     *
     * @param id
     */
    @Override
    public void reminder(Long id) {

        Long userId = BaseContext.getCurrentId();

        // 1. 查询订单
        Orders order = orderMapper.getByIdAndUserId(id, userId);

        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }

        // 2. 构造催单消息
        Map<String, Object> map = new HashMap<>();

        // type = 2 表示催单
        map.put("type", 2);
        map.put("orderId", order.getId());
        map.put("content", "订单号：" + order.getNumber());

        // 3. 转 JSON
        String json = JSON.toJSONString(map);

        log.info("准备向管理端推送催单提醒：{}", json);

        // 4. WebSocket 推送给管理端
        webSocketServer.sendToAllClient(json);
    }

}