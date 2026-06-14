package com.fine.serviceIMPL;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fine.Dao.SampleFeedbackMapper;
import com.fine.modle.SampleFeedback;
import com.fine.service.SampleFeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class SampleFeedbackServiceImpl implements SampleFeedbackService {

    @Autowired
    private SampleFeedbackMapper feedbackMapper;

    @Override
    public List<SampleFeedback> getFeedbacksBySampleOrderId(Long sampleOrderId) {
        return feedbackMapper.selectList(new LambdaQueryWrapper<SampleFeedback>()
                .eq(SampleFeedback::getSampleOrderId, sampleOrderId)
                .orderByDesc(SampleFeedback::getFeedbackDate));
    }

    @Override
    @Transactional
    public boolean addFeedback(SampleFeedback feedback) {
        if (feedback.getFeedbackDate() == null) {
            feedback.setFeedbackDate(new Date());
        }
        return feedbackMapper.insert(feedback) > 0;
    }

    @Override
    @Transactional
    public boolean deleteFeedback(Long id) {
        return feedbackMapper.deleteById(id) > 0;
    }
}
