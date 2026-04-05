package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.rd.TapeSpecMapper;
import com.fine.Dao.production.ProcessParamsMapper;
import com.fine.modle.rd.TapeSpec;
import com.fine.model.production.ProcessParams;
import com.fine.service.production.ProcessParamsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 工艺参数Service实现类
 */
@Service
public class ProcessParamsServiceImpl extends ServiceImpl<ProcessParamsMapper, ProcessParams> 
        implements ProcessParamsService {

    @Autowired
    private ProcessParamsMapper paramsMapper;

    @Autowired
    private TapeSpecMapper tapeSpecMapper;

    @Override
    public IPage<ProcessParams> getProcessParamsPage(String materialCode, String processType, String equipmentCode, Integer page, Integer size) {
        IPage<ProcessParams> pageRequest = new Page<>(page, size);
        
        // 使用MyBatis-Plus的分页查询
        IPage<ProcessParams> pageResult = paramsMapper.selectProcessParamsPageList(
            pageRequest, materialCode, processType, equipmentCode);
        
        // 设置工序类型名称
        for (ProcessParams params : pageResult.getRecords()) {
            params.setProcessTypeName(getProcessTypeName(params.getProcessType()));
        }
        
        return pageResult;
    }

    @Override
    public Map<String, Object> getParamsList(String materialCode, String processType, String equipmentCode, Integer page, Integer size) {
        IPage<ProcessParams> pageRequest = new Page<>(page, size);
        IPage<ProcessParams> pageResult = paramsMapper.selectProcessParamsPageList(pageRequest, materialCode, processType, equipmentCode);

        List<ProcessParams> list = pageResult.getRecords();
        // 设置工序类型名称
        for (ProcessParams params : list) {
            params.setProcessTypeName(getProcessTypeName(params.getProcessType()));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", pageResult.getTotal());
        result.put("pageNum", pageResult.getCurrent());
        result.put("pageSize", pageResult.getSize());
        return result;
    }

    @Override
    public ProcessParams getByMaterialAndProcess(String materialCode, String processType, String equipmentCode) {
        ProcessParams params = paramsMapper.selectByMaterialAndProcess(materialCode, processType, equipmentCode);
        if (params != null) {
            params.setProcessTypeName(getProcessTypeName(params.getProcessType()));
        }
        return params;
    }

    @Override
    public List<ProcessParams> getByMaterialCode(String materialCode) {
        List<ProcessParams> list = paramsMapper.selectByMaterialCode(materialCode);
        for (ProcessParams params : list) {
            params.setProcessTypeName(getProcessTypeName(params.getProcessType()));
        }
        return list;
    }

    @Override
    @Transactional
    public boolean addParams(ProcessParams params) {
        validateMaterialCodeFromRd(params.getMaterialCode());
        params.setEquipmentCode(normalizeEquipmentCode(params.getEquipmentCode()));
        // 检查是否已存在
        int count = paramsMapper.checkExists(params.getMaterialCode(), params.getProcessType(), params.getEquipmentCode(), 0L);
        if (count > 0) {
            throw new RuntimeException("该料号的" + getProcessTypeName(params.getProcessType()) + "工艺参数已存在");
        }

        // 若唯一键记录已存在但状态=0，则直接复用并激活，避免唯一索引冲突
        ProcessParams existed = paramsMapper.selectAnyByUnique(params.getMaterialCode(), params.getProcessType(), params.getEquipmentCode());
        if (existed != null) {
            params.setId(existed.getId());
            params.setStatus(1);
            params.setUpdateTime(new Date());
            return paramsMapper.updateById(params) > 0;
        }

        params.setStatus(1);
        params.setCreateTime(new Date());
        params.setUpdateTime(new Date());
        return paramsMapper.insert(params) > 0;
    }

    @Override
    @Transactional
    public boolean updateParams(ProcessParams params) {
        validateMaterialCodeFromRd(params.getMaterialCode());
        params.setEquipmentCode(normalizeEquipmentCode(params.getEquipmentCode()));
        // 检查是否重复
        int count = paramsMapper.checkExists(params.getMaterialCode(), params.getProcessType(), params.getEquipmentCode(), params.getId());
        if (count > 0) {
            throw new RuntimeException("该料号的" + getProcessTypeName(params.getProcessType()) + "工艺参数已存在");
        }

        params.setUpdateTime(new Date());
        return paramsMapper.updateById(params) > 0;
    }

    @Override
    @Transactional
    public boolean deleteParams(Long id) {
        ProcessParams params = new ProcessParams();
        params.setId(id);
        params.setStatus(0);
        params.setUpdateTime(new Date());
        return paramsMapper.updateById(params) > 0;
    }

    @Override
    @Transactional
    public boolean batchSaveParams(String materialCode, List<ProcessParams> paramsList) {
        validateMaterialCodeFromRd(materialCode);
        // 删除原有参数
        List<ProcessParams> existing = paramsMapper.selectByMaterialCode(materialCode);
        for (ProcessParams p : existing) {
            p.setStatus(0);
            p.setUpdateTime(new Date());
            paramsMapper.updateById(p);
        }

        // 新增参数
        for (ProcessParams params : paramsList) {
            params.setMaterialCode(materialCode);
            validateMaterialCodeFromRd(params.getMaterialCode());
            params.setEquipmentCode(normalizeEquipmentCode(params.getEquipmentCode()));

            ProcessParams existed = paramsMapper.selectAnyByUnique(params.getMaterialCode(), params.getProcessType(), params.getEquipmentCode());
            if (existed != null) {
                params.setId(existed.getId());
                params.setStatus(1);
                params.setUpdateTime(new Date());
                paramsMapper.updateById(params);
            } else {
                params.setStatus(1);
                params.setCreateTime(new Date());
                params.setUpdateTime(new Date());
                paramsMapper.insert(params);
            }
        }

        return true;
    }

    private String getProcessTypeName(String processType) {
        if (processType == null) return "";
        switch (processType) {
            case "COATING": return "涂布";
            case "REWINDING": return "复卷";
            case "SLITTING": return "分切";
            case "STRIPPING": return "分条";
            default: return processType;
        }
    }

    // ==================== 导入导出 ====================

    @Override
    public List<ProcessParams> getParamsListForExport(String materialCode, String processType, String equipmentCode) {
        List<ProcessParams> list = paramsMapper.selectParamsList(materialCode, processType, equipmentCode);
        for (ProcessParams params : list) {
            params.setProcessTypeName(getProcessTypeName(params.getProcessType()));
        }
        return list;
    }

    @Override
    @Transactional
    public Map<String, Object> importParams(org.springframework.web.multipart.MultipartFile file) throws Exception {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int failCount = 0;
        StringBuilder errorMsg = new StringBuilder();

        try (org.apache.poi.ss.usermodel.Workbook workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(file.getInputStream())) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            for (int i = 1; i <= lastRow; i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    ProcessParams params = new ProcessParams();
                    // 产品料号
                    params.setMaterialCode(getCellStringValue(row.getCell(0)));
                    if (params.getMaterialCode() == null || params.getMaterialCode().isEmpty()) {
                        errorMsg.append("第").append(i + 1).append("行：产品料号不能为空\n");
                        failCount++;
                        continue;
                    }
                    try {
                        validateMaterialCodeFromRd(params.getMaterialCode());
                    } catch (RuntimeException ex) {
                        errorMsg.append("第").append(i + 1).append("行：").append(ex.getMessage()).append("\n");
                        failCount++;
                        continue;
                    }

                    // 工序类型
                    params.setProcessType(getCellStringValue(row.getCell(1)));
                    if (params.getProcessType() == null || params.getProcessType().isEmpty()) {
                        errorMsg.append("第").append(i + 1).append("行：工序类型不能为空\n");
                        failCount++;
                        continue;
                    }

                    params.setEquipmentCode(normalizeEquipmentCode(params.getEquipmentCode()));

                    // 检查是否已存在
                    if (paramsMapper.checkExists(params.getMaterialCode(), params.getProcessType(), params.getEquipmentCode(), 0L) > 0) {
                        errorMsg.append("第").append(i + 1).append("行：该工艺参数已存在\n");
                        failCount++;
                        continue;
                    }

                    // 涂布参数
                    params.setCoatingSpeed(getCellBigDecimalValue(row.getCell(2)));
                    params.setOvenTemp(getCellBigDecimalValue(row.getCell(3)));
                    params.setCoatingThickness(getCellBigDecimalValue(row.getCell(4)));
                    params.setColorChangeTime(getCellIntValue(row.getCell(5)));
                    params.setThicknessChangeTime(getCellIntValue(row.getCell(6)));
                    
                    // 复卷参数
                    params.setRewindingSpeed(getCellBigDecimalValue(row.getCell(7)));
                    params.setTensionSetting(getCellBigDecimalValue(row.getCell(8)));
                    params.setRollChangeTime(getCellIntValue(row.getCell(9)));
                    
                    // 分切参数
                    params.setSlittingSpeed(getCellBigDecimalValue(row.getCell(10)));
                    params.setBladeType(getCellStringValue(row.getCell(11)));
                    params.setBladeChangeTime(getCellIntValue(row.getCell(12)));
                    params.setMinSlitWidth(getCellIntValue(row.getCell(13)));
                    params.setMaxBlades(getCellIntValue(row.getCell(14)));
                    params.setEdgeLoss(getCellIntValue(row.getCell(15)));
                    
                    // 分条参数
                    params.setStrippingSpeed(getCellBigDecimalValue(row.getCell(16)));
                    
                    // 通用参数
                    params.setFirstCheckTime(getCellIntValue(row.getCell(17)));
                    params.setLastCheckTime(getCellIntValue(row.getCell(18)));
                    params.setSetupTime(getCellIntValue(row.getCell(19)));
                    params.setRemark(getCellStringValue(row.getCell(20)));

                    params.setStatus(1);
                    params.setCreateTime(new Date());
                    params.setUpdateTime(new Date());

                    ProcessParams existed = paramsMapper.selectAnyByUnique(params.getMaterialCode(), params.getProcessType(), params.getEquipmentCode());
                    if (existed != null) {
                        params.setId(existed.getId());
                        paramsMapper.updateById(params);
                    } else {
                        paramsMapper.insert(params);
                    }
                    successCount++;
                } catch (Exception e) {
                    errorMsg.append("第").append(i + 1).append("行：").append(e.getMessage()).append("\n");
                    failCount++;
                }
            }
        }

        result.put("success", failCount == 0);
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("message", failCount > 0 ? errorMsg.toString() : "导入成功");
        return result;
    }

    private void validateMaterialCodeFromRd(String materialCode) {
        if (materialCode == null || materialCode.trim().isEmpty()) {
            throw new RuntimeException("料号不能为空");
        }
        TapeSpec spec = tapeSpecMapper.selectByMaterialCode(materialCode.trim());
        if (spec == null) {
            throw new RuntimeException("料号不存在于研发表，请先维护研发规格");
        }
        if (spec.getStatus() == null || spec.getStatus() != 1) {
            throw new RuntimeException("料号在研发表中未启用，不能用于工艺参数");
        }
    }

    // 辅助方法：获取单元格字符串值
    private String getCellStringValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        // cell.setCellType(org.apache.poi.ss.usermodel.CellType.STRING); // Deprecated
        return cell.getStringCellValue().trim();
    }

    // 辅助方法：获取单元格整数值
    private Integer getCellIntValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            } else {
                String val = cell.getStringCellValue().trim();
                return val.isEmpty() ? null : Integer.parseInt(val);
            }
        } catch (Exception e) {
            return null;
        }
    }

    // 辅助方法：获取单元格BigDecimal值
    private java.math.BigDecimal getCellBigDecimalValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                return java.math.BigDecimal.valueOf(cell.getNumericCellValue());
            } else {
                String val = cell.getStringCellValue().trim();
                return val.isEmpty() ? null : new java.math.BigDecimal(val);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeEquipmentCode(String equipmentCode) {
        if (equipmentCode == null) {
            return null;
        }
        String s = equipmentCode.trim();
        return s.isEmpty() ? null : s;
    }
}
