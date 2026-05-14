package com.fine.service;

import com.fine.MesApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Integration test - run manually when DB is available")
@SpringBootTest(classes = MesApplication.class)
public class ArServiceIntegrationTest {

    @Autowired(required = false)
    private ArService arService;

    @Test
    public void contextLoads_andArServiceBeanPresent() {
        Assertions.assertNotNull(arService, "ArService should be available in application context");
    }

}
