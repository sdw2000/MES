package com.fine.service;

import com.fine.MesApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Integration test - run manually when DB is available")
@SpringBootTest(classes = MesApplication.class)
public class BankServiceIntegrationTest {

    @Autowired(required = false)
    private BankService bankService;

    @Test
    public void contextLoads_andBankServiceBeanPresent() {
        Assertions.assertNotNull(bankService, "BankService should be available in application context");
    }

}
