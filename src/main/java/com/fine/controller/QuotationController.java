package com.fine.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fine.Utils.ResponseResult;
import com.fine.modle.Quotation;
import com.fine.service.QuotationService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 报价单管理控制器
 */
@RestController
@RequestMapping("/quotation")
@PreAuthorize("hasAuthority('admin')")
public class QuotationController {
	
	@Autowired
   private	QuotationService quotationService; 
	
	// ============= 新增：完整的报价单CRUD接口 =============
	
	/**
	 * 获取所有报价单列表
	 */
	@GetMapping("/list")
	public ResponseResult<?> getAllQuotations() {
		return quotationService.getAllQuotations();
	}
	
	/**
	 * 根据ID获取报价单详情
	 */
	@GetMapping("/detail/{quotationId}")
	public ResponseResult<?> getQuotationById(@PathVariable Long quotationId) {
		return quotationService.getQuotationById(quotationId);
	}
	
	/**
	 * 创建报价单
	 */
	@PostMapping("/create")
	@Transactional
	public ResponseResult<?> createQuotation(@RequestBody Quotation quotation) {
		return quotationService.createQuotation(quotation);
	}
	
	/**
	 * 更新报价单
	 */
	@PutMapping("/update")
	@Transactional
	public ResponseResult<?> updateQuotation(@RequestBody Quotation quotation) {
		return quotationService.updateQuotation(quotation);
	}
	
	/**
	 * 删除报价单
	 */
	@DeleteMapping("/delete/{quotationId}")
	@Transactional
	public ResponseResult<?> deleteQuotation(@PathVariable Long quotationId) {
		return quotationService.deleteQuotation(quotationId);
	}
	
	// ============= 保留原有接口 =============
	
	@PostMapping("/upload")
    public ResponseResult<?> uploadFile(@RequestParam("file") MultipartFile file) {
    	System.out.println("jinlaile "+file.getSize());
        try {
        	quotationService.save(file);
            return new ResponseResult<>(20000, "操作成功");
        } catch (Exception e) {
            return new ResponseResult<>(50000, "操作失败");
        }
    }
	
	@GetMapping("/searchTapeByKeyWord")
	public ResponseResult<?> searchTapebyKeyWord(@RequestParam("keyword") String keyword) {
		System.out.println(keyword);
		
		return quotationService.searchTableByKeyWord(keyword);
	}
	
	@GetMapping("/getOrdersQuotationNumble")
	public ResponseResult<?> getOrdersQuotationByNumble(@RequestParam("id") String id) {
		System.out.println(id);
		
		return quotationService.getOrdersQuotationByNumble(id);
	}
	
	@PostMapping("/with-details")
    @Transactional
    public ResponseResult<?> updateQuotationdetail( @RequestBody Quotation quotation) {
    	
        System.out.println(quotation);
    	return quotationService.insert(quotation);
    }
	
	 @DeleteMapping("delete/{quotationDetailId}/{id}")
	    @Transactional
	    public ResponseResult<?> deleteQuotation(@PathVariable String quotationDetailId,@PathVariable String id) {
	    	return quotationService.deleteQuotationDetails(quotationDetailId,id);
	    }    /**
     * 根据料号获取材料类别及动态字段
     */
    @GetMapping("/material-info/{materialCode}")
    public ResponseResult<?> getMaterialInfoByCode(@PathVariable String materialCode) {
        // 1. 查询材料类别（假设有service方法）
        String category = quotationService.getCategoryByMaterialCode(materialCode);

        // 2. 根据类别返回不同字段
        Map<String, Object> result = new HashMap<>();
        result.put("category", category);

        if ("胶带".equals(category) || "薄膜".equals(category)) {
            result.put("fields", Arrays.asList("length", "width", "height", "quantity"));
        } else if ("胶水".equals(category)) {
            result.put("fields", Arrays.asList("packageSpec", "quantity"));
        } else {
            result.put("fields", Arrays.asList("quantity"));
        }

        return new ResponseResult<>(20000, "查询成功", result);
    }
    
    /**
     * 导入报价单
     */
    @PostMapping("/import")
    public ResponseResult<?> importQuotations(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return new ResponseResult<>(400, "请选择文件");
            }
            return quotationService.importFromExcel(file);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "导入失败：" + e.getMessage());
        }
    }
    
    /**
     * 导出报价单
     */
    @GetMapping("/export")
    public ResponseResult<?> exportQuotations() {
        return quotationService.exportQuotations();
    }
}
