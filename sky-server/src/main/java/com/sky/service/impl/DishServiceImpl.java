package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Category;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.CategoryService;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;



@Service
@Slf4j
public class DishServiceImpl implements DishService {
    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    /**
     * 新增菜品和对应的口味
     *
     * @param dishDTO
     */
    @Transactional//事务注解
    public void saveWithFlavor(DishDTO dishDTO) {
        //向菜品表插入1条数据
        Dish dish = new Dish();//创建Dish，但属性为空
        BeanUtils.copyProperties(dishDTO, dish);//属性拷贝
        dishMapper.insert(dish);//向菜品表插入1条数据
        Long dishId = dish.getId();//dish 里面会有 id，MyBatis会把数据库自增生成的 id 塞回 dish.id
        //刚刚新增菜品的数据库主键。
        //向口味表插入n条数据
        List<DishFlavor> flavors = dishDTO.getFlavors();//从 DTO 里拿出前端传来的所有口味。
        if (flavors != null && flavors.size() > 0) { //确实有口味数据，才继续插入口味表。
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishId);//给每一个 DishFlavor 都设置dishId = 刚刚新增菜品的id
            });
            dishFlavorMapper.insertBatch(flavors);//一次性把这些口味批量插入   List<DishFlavor> flavors
        }
    }

    /**
     * 菜品分页查询
     *
     * @param dishPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);//拿着DTO去查，返回结果保存到page<DVO>
        //装的是“分页查询结果”DishVO,DishVO` 是专门给前端展示的数据对象。
        return new PageResult(page.getTotal(), page.getResult());
    }
    @Autowired
    private SetmealDishMapper setmealDishMapper;


    /**
     * 菜品批量删除
     *
     * @param ids
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        for(Long id:ids){
            //判断当前菜品是否能够删除---是否存在起售中的菜品？？
            Dish dish = dishMapper.getById(id);
            if(dish.getStatus() == StatusConstant.ENABLE){
                //当前菜品处于起售中，不能删除
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);

            }
        }
        //判断当前菜品是否能够删除---是否被套餐关联了？？
        List<Long> setmealIds =setmealDishMapper.getSetmealIdsByDishIds(ids);
        if(setmealIds != null && setmealIds.size() > 0){
            //当前菜品被套餐关联了，不能删除
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }
        for(Long id:ids){
            dishMapper.deleteById(id);//后绪步骤实现
            dishFlavorMapper.deleteByDishId(id);//后绪步骤实现
        }

    }
    /**
     * 根据id查询菜品和对应的口味数据
     *
     * @param id
     * @return
     */
    @Override
    public DishVO getByIdWithFlavor(Long id) {
        //根据id查询菜品数据
        Dish dish = dishMapper.getById(id);
        //根据菜品id查询口味数据
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);
        //将查询到的数据封装到VO
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(dishFlavors);//给 dishVO 的 flavors 属性赋值。
        return dishVO;
    }
    /**
     * 根据id修改菜品基本信息和对应的口味信息
     *
     * @param dishDTO
     */
    @Override
    public void updateWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);

        //修改菜品表基本信息
        dishMapper.update(dish);

        //删除原有的口味数据
        dishFlavorMapper.deleteByDishId(dishDTO.getId());

        //重新插入口味数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishDTO.getId());
            });
            //向口味表插入n条数据
            dishFlavorMapper.insertBatch(flavors);
        }
    }
}