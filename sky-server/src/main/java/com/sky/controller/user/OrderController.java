
package com.sky.controller.user;

import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Slf4j
@Api(tags = "C端-订单接口")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 用户下单
     */
    @PostMapping("/submit")
    @ApiOperation("用户下单")
    public Result<OrderSubmitVO> submit(
            @RequestBody OrdersSubmitDTO ordersSubmitDTO) {

        log.info("用户下单：{}", ordersSubmitDTO);

        OrderSubmitVO orderSubmitVO =
                orderService.submitOrder(ordersSubmitDTO);

        return Result.success(orderSubmitVO);
    }

    /**
     * 订单支付
     */
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(
            @RequestBody OrdersPaymentDTO ordersPaymentDTO)
            throws Exception {

        log.info("订单支付：{}", ordersPaymentDTO);

        OrderPaymentVO orderPaymentVO =
                orderService.payment(ordersPaymentDTO);

        return Result.success(orderPaymentVO);
    }

    /**
     * 历史订单分页查询
     */
    @GetMapping("/historyOrders")
    @ApiOperation("历史订单分页查询")
    public Result<PageResult> pageQuery(
            OrdersPageQueryDTO ordersPageQueryDTO) {

        PageResult pageResult =
                orderService.pageQuery(ordersPageQueryDTO);

        return Result.success(pageResult);
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> orderDetail(
            @PathVariable Long id) {

        log.info("查询订单详情：{}", id);

        OrderVO orderVO = orderService.details(id);

        return Result.success(orderVO);
    }

    /**
     * 用户取消订单
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public Result cancel(@PathVariable Long id) {

        log.info("用户取消订单：{}", id);

        orderService.cancel(id);

        return Result.success();
    }

    /**
     * 再来一单
     */
    @PostMapping("/repetition/{id}")
    @ApiOperation("再来一单")
    public Result repetition(@PathVariable Long id) {

        log.info("用户再来一单：{}", id);

        orderService.repetition(id);

        return Result.success();
    }

    /**
     * 用户催单
     *
     * @param id
     * @return
     */
    @GetMapping("/reminder/{id}")
    @ApiOperation("用户催单")
    public Result reminder(@PathVariable("id") Long id) {
        orderService.reminder(id);
        return Result.success();
    }

}
