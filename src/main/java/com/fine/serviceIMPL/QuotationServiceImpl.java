package com.fine.serviceIMPL;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.QuotationItemMapper;
import com.fine.Dao.QuotationMapper;
import com.fine.Dao.TapeMapper;
import com.fine.Utils.QuotationDataListener;
import com.fine.Utils.ResponseResult;
import com.fine.modle.LoginUser;
import com.fine.modle.Quotation;
import com.fine.modle.QuotationItem;
import com.fine.modle.Tape;
import com.fine.service.QuotationService;

@Service
public class QuotationServiceImpl extends ServiceImpl<QuotationMapper, Quotation> implements QuotationService {
	@Autowired
	private QuotationMapper quotationMapper;
	@Autowired
	private TapeMapper tapeMapper;
	@Autowired
	private QuotationItemMapper quotationItemMapper;

	// ============= 新增方法：完整的报价单CRUD =============

	@Override
	public ResponseResult<?> getAllQuotations() {
		try {
			// 查询所有未删除的报价单
			LambdaQueryWrapper<Quotation> queryWrapper = new LambdaQueryWrapper<>();
			queryWrapper.eq(Quotation::getIsDeleted, 0).orderByDesc(Quotation::getCreatedAt);
			List<Quotation> quotations = quotationMapper.selectList(queryWrapper);

			// 为每个报价单加载明细
			for (Quotation quotation : quotations) {			LambdaQueryWrapper<QuotationItem> itemWrapper = new LambdaQueryWrapper<>();
				itemWrapper.eq(QuotationItem::getQuotationId, quotation.getId()).eq(QuotationItem::getIsDeleted, 0);
				List<QuotationItem> items = quotationItemMapper.selectList(itemWrapper);
				quotation.setItems(items);
			}
			// 返回数据
			Map<String, Object> data = new HashMap<>();
			data.put("data", quotations);

			return new ResponseResult<>(200, "获取报价单列表成功", data);} catch (Exception e) {
			e.printStackTrace();
			return new ResponseResult<>(500, "获取报价单列表失败: " + e.getMessage(), null);
		}
	}

	@Override
	public ResponseResult<?> getQuotationById(Long quotationId) {
		try {
			// 查询报价�?
			Quotation quotation = quotationMapper.selectById(quotationId);
			if (quotation == null || quotation.getIsDeleted() == 1) {
				return new ResponseResult<>(404, "报价单不存在", null);
			}

			// 查询报价单明�?
			LambdaQueryWrapper<QuotationItem> itemWrapper = new LambdaQueryWrapper<>();
			itemWrapper.eq(QuotationItem::getQuotationId, quotationId).eq(QuotationItem::getIsDeleted, 0);			List<QuotationItem> items = quotationItemMapper.selectList(itemWrapper);
			quotation.setItems(items);

			return new ResponseResult<>(200, "获取报价单详情成功", quotation);		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseResult<>(500, "获取报价单详情失败: " + e.getMessage(), null);
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ResponseResult<?> createQuotation(Quotation quotation) {
		try {
			// 获取当前登录用户
			String currentUser = getCurrentUsername();

			// 生成报价单号
			String quotationNo = generateQuotationNo();
			quotation.setQuotationNo(quotationNo);

			// 设置创建信息
			quotation.setCreatedBy(currentUser);
			quotation.setUpdatedBy(currentUser);
			quotation.setCreatedAt(new Date());
			quotation.setUpdatedAt(new Date());
			quotation.setIsDeleted(0);			// 设置默认状态
			if (quotation.getStatus() == null || quotation.getStatus().isEmpty()) {
				quotation.setStatus("draft");
			}

			// 计算总金额和总面积
			calculateTotals(quotation);

			// 保存报价�?			quotationMapper.insert(quotation);

			// 保存报价单明细
			if (quotation.getItems() != null && !quotation.getItems().isEmpty()) {
				for (QuotationItem item : quotation.getItems()) {
					item.setQuotationId(quotation.getId());
					item.setCreatedBy(currentUser);
					item.setUpdatedBy(currentUser);
					item.setCreatedAt(new Date());
					item.setUpdatedAt(new Date());
					item.setIsDeleted(0);

					// 计算明细金额
					calculateItemAmount(item);

					quotationItemMapper.insert(item);
				}
			}

			return new ResponseResult<>(200, "创建报价单成功", quotation);
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseResult<>(500, "创建报价单失败: " + e.getMessage(), null);
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ResponseResult<?> updateQuotation(Quotation quotation) {		try {
			// 验证报价单是否存在
			Quotation existingQuotation = quotationMapper.selectById(quotation.getId());
			if (existingQuotation == null || existingQuotation.getIsDeleted() == 1) {
				return new ResponseResult<>(404, "报价单不存在", null);
			}

			// 获取当前登录用户
			String currentUser = getCurrentUsername();

			// 更新报价单信�?
			quotation.setUpdatedBy(currentUser);
			quotation.setUpdatedAt(new Date());			// 计算总金额和总面积
			calculateTotals(quotation);

			// 更新报价单
			quotationMapper.updateById(quotation);

			// 删除原有明细（逻辑删除�?
			LambdaQueryWrapper<QuotationItem> deleteWrapper = new LambdaQueryWrapper<>();
			deleteWrapper.eq(QuotationItem::getQuotationId, quotation.getId());
			quotationItemMapper.delete(deleteWrapper);

			// 保存新的明细
			if (quotation.getItems() != null && !quotation.getItems().isEmpty()) {
				for (QuotationItem item : quotation.getItems()) {
					item.setId(null); // 确保是新插入
					item.setQuotationId(quotation.getId());
					item.setCreatedBy(currentUser);
					item.setUpdatedBy(currentUser);
					item.setCreatedAt(new Date());
					item.setUpdatedAt(new Date());
					item.setIsDeleted(0);

					// 计算明细金额
					calculateItemAmount(item);

					quotationItemMapper.insert(item);				}
			}

			return new ResponseResult<>(200, "更新报价单成功", quotation);
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseResult<>(500, "更新报价单失败: " + e.getMessage(), null);
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ResponseResult<?> deleteQuotation(Long quotationId) {
		try {
			// 验证报价单是否存�?
			Quotation quotation = quotationMapper.selectById(quotationId);
			if (quotation == null || quotation.getIsDeleted() == 1) {
				return new ResponseResult<>(404, "报价单不存在", null);
			}

			// 逻辑删除报价单（使用MyBatis-Plus的逻辑删除�?
			quotationMapper.deleteById(quotationId);

			// 逻辑删除报价单明�?
			LambdaQueryWrapper<QuotationItem> wrapper = new LambdaQueryWrapper<>();
			wrapper.eq(QuotationItem::getQuotationId, quotationId);
			quotationItemMapper.delete(wrapper);

			return new ResponseResult<>(200, "删除成功", null);
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseResult<>(500, "删除报价单失�? " + e.getMessage(), null);
		}	}

	/**
	 * 生成报价单号：QT-YYYYMMDD-001
	 */
	private String generateQuotationNo() {
		String prefix = "QT-" + new SimpleDateFormat("yyyyMMdd").format(new Date()) + "-";

		// 查询今天已有的最大序�?
		String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
		LambdaQueryWrapper<Quotation> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.likeRight(Quotation::getQuotationNo, "QT-" + today).orderByDesc(Quotation::getQuotationNo)
				.last("LIMIT 1");

		Quotation lastQuotation = quotationMapper.selectOne(queryWrapper);

		int sequence = 1;
		if (lastQuotation != null && lastQuotation.getQuotationNo() != null) {
			String lastNo = lastQuotation.getQuotationNo();
			String[] parts = lastNo.split("-");
			if (parts.length == 3) {
				try {
					sequence = Integer.parseInt(parts[2]) + 1;
				} catch (NumberFormatException e) {
					sequence = 1;
				}
			}
		}

		return prefix + String.format("%03d", sequence);
	}
	/**
	 * 计算报价单明细的金额和面�?
	 * 注意：已简化，不再自动计算quantity、sqm、amount
	 */
	private void calculateItemAmount(QuotationItem item) {
		// 不再需要计算，这些字段已从数据库删�?
		// 保留此方法以避免编译错误
	}

	/**
	 * 计算报价单总金额和总面�?
	 * 注意：已简化，需要手动设置总金额和总面�?
	 */
	private void calculateTotals(Quotation quotation) {
		// 不再自动计算，因为明细中没有amount和sqm字段
		// 如需设置总金额和总面积，请在前端或业务逻辑中手动设�?
		if (quotation.getTotalAmount() == null) {
			quotation.setTotalAmount(BigDecimal.ZERO);
		}
		if (quotation.getTotalArea() == null) {
			quotation.setTotalArea(BigDecimal.ZERO);
		}
	}
	/**
	 * 获取当前登录用户�?
	 */
	private String getCurrentUsername() {
		try {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
				LoginUser loginUser = (LoginUser) authentication.getPrincipal();
				return loginUser.getUser().getUsername();  // 修复：username而非userName
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "系统";
	}

	// ============= 保留原有方法 =============

	@Override	@Transactional
	public void save(MultipartFile file) {
		try {
			System.out.println("进来这里了");
			EasyExcel.read(file.getInputStream(), Quotation.class, new QuotationDataListener(quotationMapper)).sheet()
					.doRead();
		} catch (Exception e) { // Catching general Exception to handle all types of exceptions
			e.printStackTrace();
			throw new RuntimeException("Error processing file", e);
		}

	}
	@Override
	public boolean saveBatch(Collection<Quotation> entityList, int batchSize) {
		// MyBatis-Plus interface method - not implemented
		return false;
	}

	@Override
	public boolean saveOrUpdateBatch(Collection<Quotation> entityList, int batchSize) {
		// MyBatis-Plus interface method - not implemented
		return false;
	}

	@Override
	public boolean updateBatchById(Collection<Quotation> entityList, int batchSize) {
		// MyBatis-Plus interface method - not implemented
		return false;
	}

	@Override
	public boolean saveOrUpdate(Quotation entity) {
		// MyBatis-Plus interface method - not implemented
		return false;
	}

	@Override
	public Quotation getOne(Wrapper<Quotation> queryWrapper, boolean throwEx) {
		// MyBatis-Plus interface method - not implemented
		return null;
	}

	@Override
	public Map<String, Object> getMap(Wrapper<Quotation> queryWrapper) {
		// MyBatis-Plus interface method - not implemented
		return null;
	}

	@Override
	public <V> V getObj(Wrapper<Quotation> queryWrapper, Function<? super Object, V> mapper) {
		// MyBatis-Plus interface method - not implemented
		return null;
	}

	@Override
	public QuotationMapper getBaseMapper() {
		// MyBatis-Plus interface method - not implemented
		return null;
	}

	@Override
	public Class<Quotation> getEntityClass() {
		// MyBatis-Plus interface method - not implemented
		return null;
	}
	@Override
	public ResponseResult<?> fetchQueryList(String customerCode, String shortName, int page, int size) {
		// 注意：此方法使用旧的QuotationDTO和关联查询，已注�?
		// 如需使用新的报价单查询，请使�?getAllQuotations() 方法
		
		// Page<QuotationDTO> quotationPage = new Page<>(page, size);
		// QueryWrapper<QuotationDTO> queryWrapper = new QueryWrapper<>();
		// if (customerCode != null && !shortName.isEmpty()) {
		//     queryWrapper.like("c.customer_code", customerCode);
		// }
		// if (customerCode != null && !shortName.isEmpty()) {
		//     queryWrapper.like("c.short_name", shortName);
		// }
		// queryWrapper.eq("q.is_deleted", 0);
		// IPage<QuotationDTO> result = quotationMapper.getQuotationDetails(quotationPage, queryWrapper);
		// return new ResponseResult<>(20000, "操作成功", result);
		
		// 暂时返回空结�?
		return new ResponseResult<>(20000, "此方法已废弃，请使用 /quotation/list 接口", null);
	}

	@Override
	public ResponseResult<?> searchTableByKeyWord(String keyword) {
		QueryWrapper<Tape> queryWrapper = new QueryWrapper<>();
		queryWrapper.like("part_number", keyword);
		List<Tape> list = new ArrayList<>();
		list = tapeMapper.selectList(queryWrapper);
		Map<String, Object> data = new HashMap<>();		data.put("data", list);
		System.out.println(data);

		return new ResponseResult<>(20000, "查询成功", data);
	}
	@Override
	@Transactional
	public ResponseResult<?> insert(Quotation quotation) {
		System.out.println(quotation);
		try {
			quotationMapper.insert(quotation);
			Long insertedId = quotation.getId();  // 修复：改为Long类型
			System.out.println(insertedId);			// 注意：旧代码使用QuotationDetail，需要保留兼容性
			// 如果使用新的QuotationItem，请调用createQuotation方法
			if (quotation.getItems() != null && !quotation.getItems().isEmpty()) {
				for (QuotationItem item : quotation.getItems()) {
					System.out.println("插入明细：" + item);
					item.setQuotationId(insertedId);
					quotationItemMapper.insert(item);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new ResponseResult<>(20000, "操作成功");
	}
	@Override
	public ResponseResult<?> deleteQuotation(String id) {
		int idInt = Integer.parseInt(id);

		Quotation quotation = quotationMapper.selectById(idInt);
		if (quotation != null) {
			quotation.setIsDeleted(1); // 假设1表示已删除状�?
			quotationMapper.updateById(quotation);

			// 使用新的QuotationItem而不是QuotationDetail
			QueryWrapper<QuotationItem> queryWrapper2 = new QueryWrapper<>();
			queryWrapper2.eq("quotation_id", idInt);
			List<QuotationItem> quotationItems = quotationItemMapper.selectList(queryWrapper2);			for (QuotationItem item : quotationItems) {
				System.out.println(item);
				item.setIsDeleted(1);
				quotationItemMapper.updateById(item);
			}

			return new ResponseResult<>(20000, "操作成功");
		} else {
			return new ResponseResult<>(50000, "报价单不存在");
		}
	}	@Override
	public ResponseResult<?> deleteQuotationDetails(String quotationDetailId, String id) {
		// 注意：此方法使用旧的QuotationDetail实体
		// 新的实现请使用QuotationItem
		
		// 尝试使用新的QuotationItem
		try {
			QuotationItem item = quotationItemMapper.selectById(Long.parseLong(quotationDetailId));
			if (item != null) {
				quotationItemMapper.deleteById(Long.parseLong(quotationDetailId));
				return new ResponseResult<>(20000, "删除明细成功", null);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return new ResponseResult<>(20000, "操作完成", null);
	}
	@Override
	public ResponseResult<?> getOrdersQuotationByNumble(String id) {
		// 使用新的方法获取报价单详情
		try {
			Long quotationId = Long.parseLong(id);
			return getQuotationById(quotationId);
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseResult<>(50000, "获取报价单失败", null);
		}
	}
		@Override
	public String getCategoryByMaterialCode(String materialCode) {
		// 根据料号前缀或特征判断材料类别
		if (materialCode == null || materialCode.isEmpty()) {
			return "其他";
		}
		
		// 根据料号规则判断类别（可根据实际业务调整）
		// 例如：1011开头的是胶带，2xxx开头的是薄膜，3xxx开头的是胶水
		if (materialCode.startsWith("1011") || materialCode.contains("PET") || materialCode.contains("胶带")) {
			return "胶带";
		} else if (materialCode.contains("膜") || materialCode.contains("FILM")) {
			return "薄膜";
		} else if (materialCode.contains("胶水") || materialCode.contains("GLUE")) {
			return "胶水";
		}
		
		// 也可以从数据库查询物料主数据获取类别
		// Tape tape = tapeMapper.selectOne(new LambdaQueryWrapper<Tape>().eq(Tape::getMaterialCode, materialCode));
		// if (tape != null) return tape.getCategory();
		
		return "其他";
	}
	
	@Override
	public ResponseResult<?> importFromExcel(MultipartFile file) {
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
					Quotation quotation = new Quotation();
					
					// 客户名称（必填）
					String customer = getCellStringValue(row.getCell(0));
					if (customer == null || customer.isEmpty()) {
						errorMsg.append("第").append(i + 1).append("行：客户名称不能为空\n");
						failCount++;
						continue;
					}
					quotation.setCustomer(customer);
							quotation.setContactPerson(getCellStringValue(row.getCell(1)));
					quotation.setContactPhone(getCellStringValue(row.getCell(2)));
					
					String quotationDateStr = getCellStringValue(row.getCell(3));
					if (quotationDateStr != null && !quotationDateStr.isEmpty()) {
						SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
						quotation.setQuotationDate(sdf.parse(quotationDateStr));
					}
					
					String validUntilStr = getCellStringValue(row.getCell(4));
					if (validUntilStr != null && !validUntilStr.isEmpty()) {
						SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
						quotation.setValidUntil(sdf.parse(validUntilStr));
					}
					
					String status = getCellStringValue(row.getCell(5));
					quotation.setStatus(status != null && !status.isEmpty() ? convertStatusToDB(status) : "draft");
					
					quotation.setRemark(getCellStringValue(row.getCell(6)));
					
					// 生成报价单号
					quotation.setQuotationNo(generateQuotationNo());
					quotation.setCreatedAt(new Date());
					quotation.setUpdatedAt(new Date());
					quotation.setIsDeleted(0);
					quotation.setCreatedBy(getCurrentUsername());

					quotationMapper.insert(quotation);
					successCount++;
				} catch (Exception e) {
					errorMsg.append("第").append(i + 1).append("行：").append(e.getMessage()).append("\n");
					failCount++;
				}
			}
		} catch (Exception e) {
			return new ResponseResult<>(500, "导入失败：" + e.getMessage());
		}

		result.put("success", failCount == 0);
		result.put("successCount", successCount);
		result.put("failCount", failCount);
		result.put("message", failCount > 0 ? errorMsg.toString() : "导入成功");
		return new ResponseResult<>(200, "导入完成", result);
	}
	
	@Override
	public ResponseResult<?> exportQuotations() {
		try {
			LambdaQueryWrapper<Quotation> wrapper = new LambdaQueryWrapper<>();
			wrapper.eq(Quotation::getIsDeleted, 0).orderByDesc(Quotation::getCreatedAt);
			List<Quotation> list = quotationMapper.selectList(wrapper);
			
			List<Map<String, Object>> exportData = new ArrayList<>();
			for (Quotation q : list) {
				Map<String, Object> map = new HashMap<>();
				map.put("quotationNo", q.getQuotationNo());
				map.put("customer", q.getCustomer());
				map.put("contactPerson", q.getContactPerson());
				map.put("contactPhone", q.getContactPhone());
				map.put("quotationDate", q.getQuotationDate());
				map.put("validUntil", q.getValidUntil());
				map.put("totalAmount", q.getTotalAmount());
				map.put("totalArea", q.getTotalArea());
				map.put("status", convertStatusToText(q.getStatus()));
				map.put("remark", q.getRemark());
				exportData.add(map);
			}
			
			return new ResponseResult<>(200, "导出成功", exportData);
		} catch (Exception e) {
			return new ResponseResult<>(500, "导出失败：" + e.getMessage());
		}
	}
		// 辅助方法：获取单元格字符串值
	private String getCellStringValue(org.apache.poi.ss.usermodel.Cell cell) {
		if (cell == null) return null;
		try {
			switch (cell.getCellType()) {
				case STRING:
					return cell.getStringCellValue().trim();
				case NUMERIC:
					if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
						SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
						return sdf.format(cell.getDateCellValue());
					}
					return String.valueOf((long) cell.getNumericCellValue());
				case BOOLEAN:
					return String.valueOf(cell.getBooleanCellValue());
				case FORMULA:
					return cell.getCellFormula();
				default:
					return null;
			}
		} catch (Exception e) {
			return null;
		}
	}
	
	// 辅助方法：状态文字转数据库值
	private String convertStatusToDB(String statusText) {
		if (statusText == null) return "draft";
		switch (statusText) {
			case "草稿": return "draft";
			case "已提交": return "submitted";
			case "已接受": return "accepted";
			case "已拒绝": return "rejected";
			case "已过期": return "expired";
			default: return "draft";
		}
	}
	
	// 辅助方法：数据库值转状态文字
	private String convertStatusToText(String status) {
		if (status == null) return "草稿";
		switch (status) {
			case "draft": return "草稿";
			case "submitted": return "已提交";
			case "accepted": return "已接受";
			case "rejected": return "已拒绝";
			case "expired": return "已过期";
			default: return status;
		}
	}
}






