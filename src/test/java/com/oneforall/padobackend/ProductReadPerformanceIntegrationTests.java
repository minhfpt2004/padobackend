package com.oneforall.padobackend;

import com.oneforall.padobackend.entity.DeliveryType;
import com.oneforall.padobackend.entity.Product;
import com.oneforall.padobackend.entity.Role;
import com.oneforall.padobackend.entity.User;
import com.oneforall.padobackend.repository.ProductRepository;
import com.oneforall.padobackend.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class ProductReadPerformanceIntegrationTests {
    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    ProductRepository products;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void publicListBatchesCollectionsAndDetailUsesOneFetchQuery() throws Exception {
        User seller = new User();
        seller.setEmail("performance-seller@pado.test");
        seller.setPasswordHash("not-used-in-this-test");
        seller.setRole(Role.USER);
        seller = users.save(seller);

        Product first = products.save(product(seller, "Visible product 1", false));
        products.save(product(seller, "Visible product 2", false));
        products.save(product(seller, "Visible product 3", false));
        Product hidden = products.save(product(seller, "Hidden product", true));
        products.flush();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].title").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("Hidden product"))));

        assertThat(statistics.getPrepareStatementCount())
                .as("list query + batched images + batched delivery options")
                .isLessThanOrEqualTo(3);

        statistics.clear();
        mvc.perform(get("/api/products/{id}", first.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Visible product 1"))
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.deliveryOptions.length()").value(2));

        assertThat(statistics.getPrepareStatementCount())
                .as("visible detail entity graph")
                .isEqualTo(1);

        mvc.perform(get("/api/products/{id}", hidden.getId()))
                .andExpect(status().isNotFound());
    }

    private Product product(User seller, String title, boolean moderationHidden) {
        Product product = new Product();
        product.setOwner(seller);
        product.setTitle(title);
        product.setBrand("PADO");
        product.setCategory("Áo");
        product.setSize("M");
        product.setColor("Xanh");
        product.setCondition("Như mới");
        product.setPrice(new BigDecimal("420000"));
        product.setOriginalPrice(new BigDecimal("1290000"));
        product.setShippingFee(new BigDecimal("30000"));
        product.setDescription("Product read performance regression test");
        product.setReason("Dọn tủ");
        product.setSellerLocation("Quận 1, TP.HCM");
        product.setSold(false);
        product.setModerationHidden(moderationHidden);
        product.getImages().add("https://example.test/product-front.jpg");
        product.getImages().add("https://example.test/product-back.jpg");
        product.getDeliveryOptions().add(DeliveryType.MEETUP);
        product.getDeliveryOptions().add(DeliveryType.SHIP);
        return product;
    }
}
