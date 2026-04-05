package com.fine.service.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.model.production.ProductionStaff;
import com.fine.model.production.StaffSkill;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ProductionStaffService extends IService<ProductionStaff> {
    
    /**
     * 获取生产人员列表
     */
    Map<String, Object> getStaffList(String staffCode, String staffName, Long teamId,
                                     Long workshopId, String status, String department,
                                     String positionName, Integer page, Integer size);
    
    /**
     * 获取生产人员分页
     */
    IPage<ProductionStaff> getStaffPage(String staffCode, String staffName, Long teamId,
                                        Long workshopId, String status, String department,
                                        String positionName, Integer page, Integer size);
    
    /**
     * 获取生产人员详情
     */
    ProductionStaff getStaffDetail(Long id);
    
    /**
     * 添加生产人员
     */
    boolean addStaff(ProductionStaff staff);
    
    /**
     * 更新生产人员
     */
    boolean updateStaff(ProductionStaff staff);
    
    /**
     * 删除生产人员
     */
    boolean deleteStaff(Long id);
    
    /**
     * 批量删除生产人员
     */
    boolean batchDeleteStaff(List<Long> ids);
    
    /**
     * 保存技能
     */
    boolean saveSkills(Long staffId, List<StaffSkill> skills);
    
    /**
     * 获取人员技能列表
     */
    List<StaffSkill> getStaffSkills(Long staffId);
    
    /**
     * 根据设备类型获取人员
     */
    List<ProductionStaff> getStaffByEquipmentType(String equipmentType);
    
    /**
     * 根据团队获取人员
     */
    List<ProductionStaff> getStaffByTeam(Long teamId);
    
    /**
     * 获取所有活跃人员
     */
    List<ProductionStaff> getAllActiveStaff();
    
    /**
     * 导出人员列表
     */
    List<ProductionStaff> getStaffListForExport(Long teamId, Long workshopId, String status,
                                                String department, String positionName);
    
    /**
     * 导入人员
     */
    Map<String, Object> importStaff(MultipartFile file) throws Exception;

    /**
     * 一次性按身份证回填性别与年龄
     */
    Map<String, Object> backfillGenderAgeByIdCard();
}
