package com.fine.serviceIMPL.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.purchase.PurchaseSampleItemMapper;
import com.fine.Dao.purchase.PurchaseSampleMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseSample;
import com.fine.modle.purchase.PurchaseSampleItem;
import com.fine.service.purchase.PurchaseSampleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Service
public class PurchaseSampleServiceImpl extends ServiceImpl<PurchaseSampleMapper, PurchaseSample> implements PurchaseSampleService {

    @Autowired
    private PurchaseSampleMapper sampleMapper;
    @Autowired
    private PurchaseSampleItemMapper itemMapper;

    @Override
    public ResponseResult<?> list(int current, int size, String supplier, String status, String trackingNumber) {
        Page<PurchaseSample> page = new Page<>(current, size);
        IPage<PurchaseSample> result = sampleMapper.selectPaged(page, supplier, status, trackingNumber);
        return ResponseResult.success(result);
    }

    @Override
    public ResponseResult<?> detail(String sampleNo) {
        LambdaQueryWrapper<PurchaseSample> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchaseSample::getSampleNo, sampleNo).eq(PurchaseSample::getIsDeleted, 0);
        PurchaseSample sample = sampleMapper.selectOne(wrapper);
        if (sample == null) {
            return new ResponseResult<>(404, "送样记录不存在");
        }
        List<PurchaseSampleItem> items = itemMapper.selectBySampleNo(sampleNo);
        sample.setItems(items);
        return ResponseResult.success(sample);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> create(PurchaseSample sample) {
        sample.setSampleNo(sample.getSampleNo() == null ? generateSampleNo() : sample.getSampleNo());
        sample.setStatus(sample.getStatus() == null ? "待发货" : sample.getStatus());
        sample.setIsDeleted(0);
        sample.setCreatedAt(LocalDateTime.now());
        sample.setUpdatedAt(LocalDateTime.now());
        sampleMapper.insert(sample);
        if (!CollectionUtils.isEmpty(sample.getItems())) {
            for (PurchaseSampleItem item : sample.getItems()) {
                item.setSampleNo(sample.getSampleNo());
                item.setIsDeleted(0);
                item.setCreatedAt(LocalDateTime.now());
                item.setUpdatedAt(LocalDateTime.now());
                itemMapper.insert(item);
            }
        }
        return ResponseResult.success(sample);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> updateSample(PurchaseSample sample) {
        LambdaQueryWrapper<PurchaseSample> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchaseSample::getSampleNo, sample.getSampleNo());
        PurchaseSample existing = sampleMapper.selectOne(wrapper);
        if (existing == null) {
            return new ResponseResult<>(404, "送样记录不存在");
        }
        sample.setId(existing.getId());
        sample.setCreatedAt(existing.getCreatedAt());
        sample.setUpdatedAt(LocalDateTime.now());
        sample.setIsDeleted(0);
        sampleMapper.updateById(sample);
        itemMapper.deleteBySampleNo(sample.getSampleNo());
        if (!CollectionUtils.isEmpty(sample.getItems())) {
            for (PurchaseSampleItem item : sample.getItems()) {
                item.setId(null);
                item.setSampleNo(sample.getSampleNo());
                item.setIsDeleted(0);
                item.setCreatedAt(LocalDateTime.now());
                item.setUpdatedAt(LocalDateTime.now());
                itemMapper.insert(item);
            }
        }
        return ResponseResult.success(sample);
    }

    @Override
    public ResponseResult<?> deleteSample(String sampleNo) {
        LambdaQueryWrapper<PurchaseSample> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchaseSample::getSampleNo, sampleNo);
        PurchaseSample sample = sampleMapper.selectOne(wrapper);
        if (sample == null) {
            return new ResponseResult<>(404, "送样记录不存在");
        }
        sample.setIsDeleted(1);
        sampleMapper.updateById(sample);
        itemMapper.deleteBySampleNo(sampleNo);
        return ResponseResult.success();
    }

    @Override
    public ResponseResult<?> updateStatus(String sampleNo, String status) {
        LambdaQueryWrapper<PurchaseSample> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchaseSample::getSampleNo, sampleNo);
        PurchaseSample sample = sampleMapper.selectOne(wrapper);
        if (sample == null) {
            return new ResponseResult<>(404, "送样记录不存在");
        }
        sample.setStatus(status);
        sample.setUpdatedAt(LocalDateTime.now());
        sampleMapper.updateById(sample);
        return ResponseResult.success();
    }

    @Override
    public String generateSampleNo() {
        String no = sampleMapper.generateSampleNo();
        if (no == null || no.isEmpty()) {
            return "PS" + System.currentTimeMillis();
        }
        return no;
    }

    @Override
    public ResponseResult<?> importSamples(MultipartFile file) {
        return new ResponseResult<>(200, "Not implemented");
    }

    @Override
    public ResponseResult<?> exportSamples(String supplier, String status) {
        return new ResponseResult<>(200, "Not implemented");
    }
}
