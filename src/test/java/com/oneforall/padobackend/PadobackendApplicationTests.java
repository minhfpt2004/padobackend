package com.oneforall.padobackend;

import com.oneforall.padobackend.config.MvpDataInitializer;
import com.oneforall.padobackend.repository.OrderRepository;
import com.oneforall.padobackend.repository.ProductRepository;
import com.oneforall.padobackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
		"app.seed.enabled=true",
		"app.seed.password=DemoPassword123!"
})
class PadobackendApplicationTests {
	@Autowired
	UserRepository users;

	@Autowired
	ProductRepository products;

	@Autowired
	OrderRepository orders;

	@Autowired
	MvpDataInitializer initializer;

	@Test
	void mvpSampleDataIsCreatedAndRemainsIdempotent() throws Exception {
		assertTrue(users.existsByEmailIgnoreCase("seller.linh@pado.test"));
		assertTrue(users.existsByEmailIgnoreCase("seller.minh@pado.test"));
		assertTrue(users.existsByEmailIgnoreCase("buyer@pado.test"));
		assertEquals(20, products.count());
		assertEquals(2, orders.count());

		initializer.run(new DefaultApplicationArguments(new String[0]));

		assertEquals(20, products.count());
		assertEquals(2, orders.count());
	}

}
