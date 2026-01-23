package com.fine.service.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.Utils.ResponseResult;
import com.fine.model.production.Equipment;
import com.fine.model.production.EquipmentType;
import com.fine.model.production.Workshop;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface EquipmentService extends IService<Equipment> {
    
    /**
     * 获取设备列表
     */
    ResponseResult<?> getEquipmentList(String equipmentType, Long workshopId, String status, String keyword, Integer pageNum, Integer pageSize);
    
    /**
     * 获取设备详情
     */
    ResponseResult<?> getEquipmentById(Long id);
    
    /**
     * 添加设备
     */
    ResponseResult<?> addEquipment(Equipment equipment);
    
    /**
     * 更新设备
     */
    ResponseResult<?> updateEquipment(Equipment equipment);
    
    /**
     * 删除设备
     */
    ResponseResult<?> deleteEquipment(Long id);
    
    /**
     * 获取所有设备类型
     */
    ResponseResult<?> getEquipmentTypes();
    
    /**
     * 获取所有车间
     */
    ResponseResult<?> getWorkshops();
    
    /**
     * 获取指定类型的可用设备
     */
    ResponseResult<?> getAvailableByType(String equipmentType);
    
    /**
     * 更新设备状态
     */
    ResponseResult<?> updateStatus(Long id, String status);
    
    // ========== 车间管理 ==========
    
    /**
     * 获取车间分页
     */
    IPage<Workshop> getWorkshopPage(Integer status, String keyword, Integer page, Integer size);
    
    /**
     * 添加车间
     */
    ResponseResult<?> addWorkshop(Workshop workshop);
    
    /**
     * 更新车间
     */
    ResponseResult<?> updateWorkshop(Workshop workshop);
    
    /**
     * 删除车间
     */
    ResponseResult<?> deleteWorkshop(Long id);
    
    // ========== 设备类型管理 ==========
    
    /**
     * 获取设备类型分页
     */
    IPage<EquipmentType> getEquipmentTypePage(Integer status, Integer page, Integer size);
    
    // ========== 导入导出 ==========
    
    /**
     * 导出设备列表
     */
    List<Equipment> getEquipmentListForExport(String equipmentType, Long workshopId, String status);
    
    /**
     * 导入设备
     */
    Map<String, Object> importEquipment(MultipartFile file) throws Exception;
}
