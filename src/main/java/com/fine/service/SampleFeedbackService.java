package com.fine.service;

import com.fine.modle.SampleFeedback;
import java.util.List;

public interface SampleFeedbackService {
    List<SampleFeedback> getFeedbacksBySampleOrderId(Long sampleOrderId);
    boolean addFeedback(SampleFeedback feedback);
    boolean deleteFeedback(Long id);
}
