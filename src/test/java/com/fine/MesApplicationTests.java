package com.fine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fine.Dao.OrderMapper;

@SpringBootTest
class MesApplicationTests {
	@Autowired
	private OrderMapper orderMapper;
	
	@Test
	void getOrder() {
		System.out.println("haha");
		System.out.println(orderMapper.getOrderByOrderNumber("DH23031301"));
	}

}