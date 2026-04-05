package com.fine.service.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.model.production.ProcessParams;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ProcessParamsService extends IService<ProcessParams> {
    
    /**
     * 获取工艺参数分页
     */
    IPage<ProcessParams> getProcessParamsPage(String materialCode, String processType, String equipmentCode, Integer page, Integer size);
    
    /**
     * 获取工艺参数列表
     */
    Map<String, Object> getParamsList(String materialCode, String processType, String equipmentCode, Integer page, Integer size);
    
    /**
     * 根据料号和工序类型获取参数
     */
    ProcessParams getByMaterialAndProcess(String materialCode, String processType, String equipmentCode);
    
    /**
     * 根据料号获取所有参数
     */
    List<ProcessParams> getByMaterialCode(String materialCode);
    
    /**
     * 添加参数
     */
    boolean addParams(ProcessParams params);
    
    /**
     * 更新参数
     */
    boolean updateParams(ProcessParams params);
    
    /**
     * 删除参数
     */
    boolean deleteParams(Long id);
    
    /**
     * 批量保存参数
     */
    boolean batchSaveParams(String materialCode, List<ProcessParams> paramsList);
    
    /**
     * 导出参数列表
     */
    List<ProcessParams> getParamsListForExport(String materialCode, String processType, String equipmentCode);
    
    /**
     * 导入参数
     */
    Map<String, Object> importParams(MultipartFile file) throws Exception;
}
