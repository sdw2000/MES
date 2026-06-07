package com.fine.serviceIMPL.rd;

import com.fine.Dao.rd.TapeFormulaMapper;
import com.fine.Dao.rd.SlittingCartonSpecMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.rd.SlittingCartonSpec;
import com.fine.modle.rd.TapeRawMaterial;
import com.fine.service.rd.SlittingCartonSpecService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SlittingCartonSpecServiceImpl implements SlittingCartonSpecService {

    @Autowired
    private SlittingCartonSpecMapper slittingCartonSpecMapper;

    @Autowired
    private TapeFormulaMapper tapeFormulaMapper;

    @Override
    public ResponseResult<?> getList(int page, int size, String materialCode, String specName, Integer status) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;

        List<SlittingCartonSpec> records = slittingCartonSpecMapper.selectList(materialCode, specName, status, offset, safeSize);
        int total = slittingCartonSpecMapper.selectCount(materialCode, specName, status);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return ResponseResult.success("查询成功", result);
    }

    @Override
    public ResponseResult<?> getById(Long id) {
        SlittingCartonSpec spec = slittingCartonSpecMapper.selectById(id);
        if (spec == null) {
            return ResponseResult.fail("记录不存在");
        }
        return ResponseResult.success("查询成功", spec);
    }

    @Override
    public ResponseResult<?> getByMaterialCode(String materialCode, Integer status) {
        String code = materialCode == null ? "" : materialCode.trim().toUpperCase();
        if (code.isEmpty()) {
            return ResponseResult.fail("料号不能为空");
        }
        List<SlittingCartonSpec> list = slittingCartonSpecMapper.selectByMaterialCode(code, status);
        return ResponseResult.success("查询成功", list);
    }

    @Override
    public ResponseResult<?> create(SlittingCartonSpec spec, String operator) {
        ResponseResult<?> valid = validate(spec, false);
        if (valid != null) return valid;

        spec.setMaterialCode(spec.getMaterialCode().trim().toUpperCase());
        if (slittingCartonSpecMapper.checkExists(spec.getMaterialCode(), spec.getSpecName().trim(), 0L) > 0) {
            return ResponseResult.fail("同料号下规格名称已存在");
        }

        spec.setStatus(spec.getStatus() == null ? 1 : spec.getStatus());
        spec.setCreateBy(operator);
        spec.setUpdateBy(operator);
        slittingCartonSpecMapper.insert(spec);
        return ResponseResult.success("新增成功", spec);
    }

    @Override
    public ResponseResult<?> update(SlittingCartonSpec spec, String operator) {
        if (spec == null || spec.getId() == null) {
            return ResponseResult.fail("ID不能为空");
        }
        ResponseResult<?> valid = validate(spec, true);
        if (valid != null) return valid;

        spec.setMaterialCode(spec.getMaterialCode().trim().toUpperCase());
        if (slittingCartonSpecMapper.checkExists(spec.getMaterialCode(), spec.getSpecName().trim(), spec.getId()) > 0) {
            return ResponseResult.fail("同料号下规格名称已存在");
        }

        SlittingCartonSpec db = slittingCartonSpecMapper.selectById(spec.getId());
        if (db == null) {
            return ResponseResult.fail("记录不存在");
        }

        spec.setUpdateBy(operator);
        slittingCartonSpecMapper.update(spec);
        return ResponseResult.success("更新成功", null);
    }

    @Override
    public ResponseResult<?> delete(Long id) {
        if (id == null) {
            return ResponseResult.fail("ID不能为空");
        }
        int rows = slittingCartonSpecMapper.deleteById(id);
        if (rows <= 0) {
            return ResponseResult.fail("删除失败，记录不存在");
        }
        return ResponseResult.success("删除成功", null);
    }

    @Override
    public ResponseResult<?> syncFromPackageStock(String operator) {
        List<TapeRawMaterial> rawMaterials = tapeFormulaMapper.selectAllRawMaterialsIncludingDisabled();

        int total = rawMaterials == null ? 0 : rawMaterials.size();
        int cartonCandidates = 0;
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int parseFailed = 0;
        List<String> parseFailedSamples = new ArrayList<>();

        Map<String, List<SlittingCartonSpec>> existingCache = new HashMap<>();
        for (TapeRawMaterial material : rawMaterials) {
            if (!isCartonMaterial(material)) {
                continue;
            }
            cartonCandidates++;

            String materialCode = upperTrim(material == null ? null : material.getMaterialCode());
            if (!StringUtils.hasText(materialCode)) {
                skipped++;
                continue;
            }

            Integer[] dims = parseDimensions(material.getSpec());
            if (dims == null) {
                dims = parseDimensions(material.getMaterialName());
            }
            if (dims == null) {
                dims = parseDimensions(material.getRemark());
            }
            if (dims == null) {
                parseFailed++;
                if (parseFailedSamples.size() < 20) {
                    parseFailedSamples.add(materialCode + "(" + safe(material.getSpec()) + ")");
                }
                continue;
            }

            String specName = buildSpecName(material, dims);
            List<SlittingCartonSpec> existing = existingCache.computeIfAbsent(materialCode,
                    k -> slittingCartonSpecMapper.selectByMaterialCode(k, null));
            SlittingCartonSpec hit = findBySpecName(existing, specName);

            String remark = buildRemark(material);
            if (hit == null) {
                SlittingCartonSpec create = new SlittingCartonSpec();
                create.setMaterialCode(materialCode);
                create.setSpecName(specName);
                create.setLengthMm(dims[0]);
                create.setWidthMm(dims[1]);
                create.setHeightMm(dims[2]);
                create.setStatus(1);
                create.setRemark(remark);
                create.setCreateBy(operator);
                create.setUpdateBy(operator);
                slittingCartonSpecMapper.insert(create);
                existing.add(create);
                inserted++;
            } else {
                boolean changed = !sameInt(hit.getLengthMm(), dims[0])
                        || !sameInt(hit.getWidthMm(), dims[1])
                        || !sameInt(hit.getHeightMm(), dims[2])
                        || hit.getStatus() == null || hit.getStatus() != 1
                        || !safe(hit.getRemark()).equals(remark);
                if (!changed) {
                    skipped++;
                    continue;
                }
                hit.setLengthMm(dims[0]);
                hit.setWidthMm(dims[1]);
                hit.setHeightMm(dims[2]);
                hit.setStatus(1);
                hit.setRemark(remark);
                hit.setUpdateBy(operator);
                slittingCartonSpecMapper.update(hit);
                updated++;
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("totalRawMaterials", total);
        data.put("cartonCandidates", cartonCandidates);
        data.put("inserted", inserted);
        data.put("updated", updated);
        data.put("skipped", skipped);
        data.put("parseFailed", parseFailed);
        data.put("parseFailedSamples", parseFailedSamples);
        return ResponseResult.success("原材料纸箱同步完成", data);
    }

    private ResponseResult<?> validate(SlittingCartonSpec spec, boolean update) {
        if (spec == null) return ResponseResult.fail("参数不能为空");
        String materialCode = spec.getMaterialCode() == null ? "" : spec.getMaterialCode().trim();
        String specName = spec.getSpecName() == null ? "" : spec.getSpecName().trim();
        if (materialCode.isEmpty()) return ResponseResult.fail("料号不能为空");
        if (specName.isEmpty()) return ResponseResult.fail("规格名称不能为空");
        if (spec.getLengthMm() == null || spec.getLengthMm() <= 0) return ResponseResult.fail("纸箱长必须大于0");
        if (spec.getWidthMm() == null || spec.getWidthMm() <= 0) return ResponseResult.fail("纸箱宽必须大于0");
        if (spec.getHeightMm() == null || spec.getHeightMm() <= 0) return ResponseResult.fail("纸箱高必须大于0");
        if (spec.getStatus() != null && spec.getStatus() != 0 && spec.getStatus() != 1) {
            return ResponseResult.fail("状态值非法");
        }
        if (update && spec.getId() == null) return ResponseResult.fail("ID不能为空");
        return null;
    }

    private String upperTrim(String text) {
        return text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
    }

    private String safe(String text) {
        return text == null ? "" : text.trim();
    }

    private boolean sameInt(Integer left, Integer right) {
        return (left == null ? 0 : left) == (right == null ? 0 : right);
    }

    private boolean isCartonMaterial(TapeRawMaterial material) {
        if (material == null) {
            return false;
        }
        String code = upperTrim(material.getMaterialCode());
        if (code.startsWith("ZX") || code.startsWith("BOX") || code.startsWith("CTN")) {
            return true;
        }
        String name = safe(material.getMaterialName()).toLowerCase(Locale.ROOT);
        String spec = safe(material.getSpec()).toLowerCase(Locale.ROOT);
        String categoryRaw = safe(material.getMaterialCategoryRaw()).toLowerCase(Locale.ROOT);
        String category = safe(material.getMaterialCategory()).toLowerCase(Locale.ROOT);
        return name.contains("纸箱") || name.contains("纸盒") || name.contains("carton") || name.contains("ctn")
                || spec.contains("纸箱") || spec.contains("纸盒") || spec.contains("carton") || spec.contains("ctn")
                || categoryRaw.contains("纸箱") || categoryRaw.contains("包材")
                || category.contains("纸箱") || category.contains("pack") || category.contains("box");
    }

    private Integer[] parseDimensions(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.replace('×', 'x').replace('X', 'x').replace('*', 'x')
                .replace("毫米", "mm").replace("MM", "mm").replace(" ", "");

        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\D{0,8}(\\d+(?:\\.\\d+)?)\\D{0,8}(\\d+(?:\\.\\d+)?)").matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        int l = toPositiveInt(matcher.group(1));
        int w = toPositiveInt(matcher.group(2));
        int h = toPositiveInt(matcher.group(3));
        if (l <= 0 || w <= 0 || h <= 0) {
            return null;
        }
        return new Integer[]{l, w, h};
    }

    private int toPositiveInt(String text) {
        try {
            double value = Double.parseDouble(text);
            int n = (int) Math.round(value);
            return Math.max(n, 0);
        } catch (Exception ex) {
            return 0;
        }
    }

    private String buildSpecName(TapeRawMaterial material, Integer[] dims) {
        String name = safe(material == null ? null : material.getMaterialName());
        if (StringUtils.hasText(name)) {
            return name;
        }
        return dims[0] + "*" + dims[1] + "*" + dims[2];
    }

    private String buildRemark(TapeRawMaterial material) {
        String spec = safe(material == null ? null : material.getSpec());
        return StringUtils.hasText(spec) ? "来源:原材料表; 原规格:" + spec : "来源:原材料表";
    }

    private SlittingCartonSpec findBySpecName(List<SlittingCartonSpec> list, String specName) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        String target = safe(specName);
        for (SlittingCartonSpec item : list) {
            if (item != null && safe(item.getSpecName()).equalsIgnoreCase(target)) {
                return item;
            }
        }
        return null;
    }
}
