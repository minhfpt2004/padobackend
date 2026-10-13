package com.oneforall.padobackend.config;

import com.oneforall.padobackend.entity.DeliveryType;
import com.oneforall.padobackend.entity.OrderStatus;
import com.oneforall.padobackend.entity.Product;
import com.oneforall.padobackend.entity.PurchaseOrder;
import com.oneforall.padobackend.entity.Role;
import com.oneforall.padobackend.entity.User;
import com.oneforall.padobackend.repository.OrderRepository;
import com.oneforall.padobackend.repository.ProductRepository;
import com.oneforall.padobackend.repository.UserRepository;
import com.oneforall.padobackend.repository.MembershipPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class MvpDataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MvpDataInitializer.class);

    private final UserRepository users;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;
    private final MembershipPlanRepository membershipPlans;

    public MvpDataInitializer(UserRepository users, ProductRepository products, OrderRepository orders,
                              PasswordEncoder passwordEncoder, MembershipPlanRepository membershipPlans,
                              @Value("${app.seed.password}") String demoPassword) {
        this.users = users;
        this.products = products;
        this.orders = orders;
        this.passwordEncoder = passwordEncoder;
        this.membershipPlans = membershipPlans;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (demoPassword == null || demoPassword.length() < 12) {
            throw new IllegalStateException("SEED_DATA_PASSWORD must contain at least 12 characters");
        }

        User linh = ensureUser("seller.linh@pado.test");
        User minh = ensureUser("seller.minh@pado.test");
        User buyer = ensureUser("buyer@pado.test");

        seed(product(linh, "Áo khoác denim vintage", "Levi's", "Áo khoác", "M", "Xanh denim",
                "Như mới", "420000", "1290000", "30000",
                "Áo khoác denim form unisex, chất vải dày vừa và không có lỗi sử dụng.",
                "Dọn tủ cuối mùa", "Quận 1, TP.HCM", 10.7756, 106.7019, false,
                List.of("https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=900"),
                Set.of(DeliveryType.MEETUP, DeliveryType.SHIP)));

        seed(product(linh, "Áo sơ mi linen tối giản", "Muji", "Áo sơ mi", "L", "Trắng kem",
                "Tốt", "280000", "690000", "25000",
                "Sơ mi linen thoáng mát, hợp đi làm và đi chơi, đã giặt sạch trước khi đăng.",
                "Không còn đúng size", "Quận 1, TP.HCM", 10.7756, 106.7019, false,
                List.of("https://images.unsplash.com/photo-1598033129183-c4f50c736f10?w=900"),
                Set.of(DeliveryType.MEETUP, DeliveryType.SHIP)));

        Product completedProduct = seed(product(linh, "Túi đeo chéo da nâu", "Charles & Keith", "Túi", "Freesize", "Nâu",
                "Như mới", "550000", "1490000", "30000",
                "Túi nhỏ gọn, khóa và dây đeo còn tốt, phù hợp dùng hằng ngày.",
                "Ít sử dụng", "Quận 1, TP.HCM", 10.7756, 106.7019, true,
                List.of("https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=900"),
                Set.of(DeliveryType.SHIP)));

        seed(product(linh, "Mũ bucket local brand", "DirtyCoins", "Phụ kiện", "Freesize", "Đen",
                "Tốt", "160000", "350000", "20000",
                "Mũ bucket màu đen dễ phối đồ, form đứng và logo còn nguyên vẹn.",
                "Đổi phong cách", "Quận 1, TP.HCM", 10.7756, 106.7019, false,
                List.of("https://images.unsplash.com/photo-1521369909029-2afed882baee?w=900"),
                Set.of(DeliveryType.MEETUP, DeliveryType.SHIP)));

        Product pendingProduct = seed(product(minh, "Đầm hoa midi mùa hè", "Zara", "Đầm", "S", "Hoa xanh",
                "Như mới", "390000", "1190000", "25000",
                "Đầm midi vải nhẹ, có lớp lót, mặc hai lần và không bị xù hay phai màu.",
                "Không còn phù hợp", "Quận 3, TP.HCM", 10.7844, 106.6844, false,
                List.of("https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=900"),
                Set.of(DeliveryType.MEETUP, DeliveryType.SHIP)));

        seedOrder(completedProduct, buyer, DeliveryType.SHIP, OrderStatus.COMPLETED,
                "25 Nguyễn Huệ, Quận 1, TP.HCM", null,
                "Đơn mẫu đã hoàn tất để kiểm tra lịch sử mua bán");
        seedOrder(pendingProduct, buyer, DeliveryType.MEETUP, OrderStatus.PENDING,
                null, "Cổng chính Công viên Lê Văn Tám",
                "Đơn mẫu đang chờ người bán xác nhận");

        seed(product(minh, "Giày sneaker trắng basic", "Adidas", "Giày", "38", "Trắng",
                "Tốt", "620000", "1800000", "35000",
                "Sneaker trắng dễ phối đồ, đế còn tốt, có hộp và dây giày dự phòng.",
                "Chật size", "Quận 3, TP.HCM", 10.7844, 106.6844, false,
                List.of("https://images.unsplash.com/photo-1549298916-b41d501d3772?w=900"),
                Set.of(DeliveryType.MEETUP, DeliveryType.SHIP)));

        seed(product(minh, "Quần jeans ống rộng", "Uniqlo", "Quần", "M", "Xanh nhạt",
                "Như mới", "310000", "799000", "25000",
                "Jeans ống rộng cạp cao, form đẹp, chất vải mềm và phù hợp đi học hoặc đi làm.",
                "Mua nhầm size", "Quận 3, TP.HCM", 10.7844, 106.6844, false,
                List.of("https://images.unsplash.com/photo-1542272604-787c3835535d?w=900"),
                Set.of(DeliveryType.SHIP)));

        seed(product(minh, "Áo thun graphic unisex", "Hades", "Áo thun", "L", "Xám",
                "Tốt", "190000", "420000", "20000",
                "Áo thun form rộng, hình in còn rõ, không rách và không có vết bẩn.",
                "Dọn tủ", "Quận 3, TP.HCM", 10.7844, 106.6844, false,
                List.of("https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=900"),
                Set.of(DeliveryType.MEETUP, DeliveryType.SHIP)));

        seed(product(linh, "Áo len cổ lọ màu be", "Uniqlo", "Áo", "M", "Be",
                "Như mới", "240000", "690000", "25000",
                "Áo len cổ lọ dệt mịn, giữ ấm tốt, không xù lông và không bai cổ.",
                "Ít mặc trong thời tiết TP.HCM", "Quận 1, TP.HCM", 10.7756, 106.7019, false,
                List.of("https://images.unsplash.com/photo-1434389677669-e08b4cac3105?w=900"),
                Set.of(DeliveryType.MEETUP, DeliveryType.SHIP)));

        seed(product(linh, "Quần kaki ống suông công sở", "COS", "Quần", "M", "Be",
                "Tốt", "330000", "1250000", "30000",
                "Quần kaki lưng cao, ống suông dễ phối áo sơ mi hoặc áo thun, đường may còn chắc chắn.",
                "Đổi phong cách", "Quận 1, TP.HCM", 10.7756, 106.7019, false,
                List.of("https://images.unsplash.com/photo-1594633312681-425c7b97ccd1?w=900"),
                Set.of(DeliveryType.MEETUP, DeliveryType.SHIP)));

        seed(product(linh, "Váy tennis xếp ly trắng", "H&M", "Váy/Đầm", "S", "Trắng",
                "Như mới", "180000", "499000", "20000",
                "Váy tennis xếp ly có quần bảo hộ bên trong, form đứng và chưa có vết ố.",
                "Mua nhầm size", "Quận 1, TP.HCM", 10.7756, 106.7019, false,
                List.of("https://images.unsplash.com/photo-1583496661160-fb5886a0aaaa?w=900"),
                Set.of(DeliveryType.MEETUP, DeliveryType.SHIP)));

        seed(product(linh, "Giày loafer da đen", "Pedro", "Giày", "38", "Đen",
                "Tốt", "480000", "1590000", "35000",
                "Loafer da mềm, đế thấp dễ đi, phần mũi và gót không trầy xước đáng kể.",
                "Không còn đi thường xuyên", "Quận 1, TP.HCM", 10.7756, 106.7019, false,
                List.of("https://images.unsplash.com/photo-1543163521-1bf539c55dd2?w=900"),
                Set.of(DeliveryType.SHIP)));

        seed(product(linh, "Túi tote canvas tối giản", "Muji", "Túi xách", "Freesize", "Trắng kem",
                "Tốt", "150000", "390000", "20000",
                "Túi tote canvas dày, có ngăn nhỏ bên trong, quai túi chắc và đã được vệ sinh sạch.",
                "Có quá nhiều túi", "Quận 1, TP.HCM", 10.7756, 106.7019, false,
                List.of("https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=900"),
                Set.of(DeliveryType.MEETUP, DeliveryType.SHIP)));

        seed(product(linh, "Áo blazer oversized màu kem", "Mango", "Áo khoác", "L", "Kem",
                "Như mới", "520000", "1690000", "30000",
                "Blazer dáng rộng có lớp lót, vai đứng form, phù hợp đi làm hoặc phối phong cách tối giản.",
                "Form rộng hơn mong đợi", "Quận 1, TP.HCM", 10.7756, 106.7019, false,
                List.of("https://images.unsplash.com/photo-1591047139829-d91aecb6caea?w=900"),
                Set.of(DeliveryType.MEETUP, DeliveryType.SHIP)));

        seed(product(minh, "Áo polo navy thêu logo", "Lacoste", "Áo", "L", "Xanh navy",
                "Tốt", "360000", "1450000", "25000",
                "Áo polo cotton thoáng, cổ áo còn đứng và logo thêu nguyên vẹn.",
                "Không còn hợp phong cách", "Quận 3, TP.HCM", 10.7844, 106.6844, false,
                List.of("https://images.unsplash.com/photo-1627225924765-552d49cf47ad?w=900"),
                Set.of(DeliveryType.MEETUP, DeliveryType.SHIP)));

        seed(product(minh, "Quần short denim xanh nhạt", "Zara", "Quần", "M", "Xanh nhạt",
                "Như mới", "220000", "699000", "25000",
                "Quần short denim cạp cao, chất vải mềm, mặc hai lần và không bị giãn form.",
                "Mua dư", "Quận 3, TP.HCM", 10.7844, 106.6844, false,
                List.of("https://images.unsplash.com/photo-1591195853828-11db59a44f6b?w=900"),
                Set.of(DeliveryType.MEETUP, DeliveryType.SHIP)));

        seed(product(minh, "Váy lụa hai dây dự tiệc", "Mango", "Váy/Đầm", "S", "Đỏ đô",
                "Như mới", "410000", "1390000", "25000",
                "Váy lụa hai dây dáng midi, có khóa kéo sau lưng, bề mặt vải không xước.",
                "Đã dùng cho một sự kiện", "Quận 3, TP.HCM", 10.7844, 106.6844, false,
                List.of("https://images.unsplash.com/photo-1566174053879-31528523f8ae?w=900"),
                Set.of(DeliveryType.MEETUP, DeliveryType.SHIP)));

        seed(product(minh, "Áo hoodie zip màu xám", "H&M", "Áo khoác", "L", "Xám",
                "Tốt", "290000", "899000", "30000",
                "Hoodie khóa kéo form rộng, nỉ mềm, khóa hoạt động tốt và bo tay chưa giãn.",
                "Dọn tủ cuối năm", "Quận 3, TP.HCM", 10.7844, 106.6844, false,
                List.of("https://images.unsplash.com/photo-1556821840-3a63f95609a7?w=900"),
                Set.of(DeliveryType.MEETUP, DeliveryType.SHIP)));

        seed(product(minh, "Giày chạy bộ Nike đỏ", "Nike", "Giày", "40", "Đỏ",
                "Tốt", "690000", "2290000", "35000",
                "Giày chạy bộ nhẹ, đệm còn đàn hồi tốt, đế chỉ mòn nhẹ và đã được vệ sinh.",
                "Đổi sang dòng giày khác", "Quận 3, TP.HCM", 10.7844, 106.6844, false,
                List.of("https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=900"),
                Set.of(DeliveryType.MEETUP, DeliveryType.SHIP)));

        seed(product(minh, "Kính mát gọng nâu cổ điển", "Ray-Ban", "Phụ kiện", "Freesize", "Nâu",
                "Như mới", "750000", "2890000", "20000",
                "Kính mát gọng nâu kiểu cổ điển, tròng không xước, đi kèm hộp và khăn lau.",
                "Ít sử dụng", "Quận 3, TP.HCM", 10.7844, 106.6844, false,
                List.of("https://images.unsplash.com/photo-1511499767150-a48a237f0083?w=900"),
                Set.of(DeliveryType.SHIP)));

        log.info("MVP sample data is ready: {} users, {} products and {} orders in the database",
                users.count(), products.count(), orders.count());
    }

    private User ensureUser(String email) {
        User user = users.findByEmailIgnoreCase(email).orElseGet(() -> {
            User created = new User();
            created.setEmail(email);
            created.setPasswordHash(passwordEncoder.encode(demoPassword));
            created.setRole(Role.USER);
            return users.save(created);
        });
        if (user.getMembershipPlan() == null) {
            user.setMembershipPlan(membershipPlans.findById("FREE").orElseThrow());
            return users.save(user);
        }
        return user;
    }

    private Product seed(Product product) {
        return products.findByOwnerIdAndTitleIgnoreCase(product.getOwner().getId(), product.getTitle())
                .orElseGet(() -> products.save(product));
    }

    private void seedOrder(Product product, User buyer, DeliveryType deliveryType, OrderStatus status,
                           String deliveryAddress, String meetupLocation, String note) {
        if (orders.existsByProductId(product.getId())) {
            return;
        }

        BigDecimal shippingFee = deliveryType == DeliveryType.SHIP
                ? product.getShippingFee()
                : BigDecimal.ZERO;
        PurchaseOrder order = new PurchaseOrder();
        order.setProductId(product.getId());
        order.setBuyerId(buyer.getId());
        order.setSellerId(product.getOwner().getId());
        order.setBuyerEmail(buyer.getEmail());
        order.setSellerEmail(product.getOwner().getEmail());
        order.setProductTitle(product.getTitle());
        order.setProductImageUrl(product.getImages().isEmpty() ? null : product.getImages().getFirst());
        order.setItemPrice(product.getPrice());
        order.setShippingFee(shippingFee);
        order.setTotalAmount(product.getPrice().add(shippingFee));
        order.setDeliveryType(deliveryType);
        order.setStatus(status);
        order.setBuyerName("Nguyễn An");
        order.setBuyerPhone("0908123456");
        order.setDeliveryAddress(deliveryAddress);
        order.setMeetupLocation(meetupLocation);
        order.setNote(note);
        if (status == OrderStatus.COMPLETED) {
            order.setCompletedAt(Instant.now());
        }
        product.setSold(status != OrderStatus.CANCELLED);
        orders.save(order);
    }

    private Product product(User owner, String title, String brand, String category, String size,
                            String color, String condition, String price, String originalPrice,
                            String shippingFee, String description, String reason,
                            String sellerLocation, Double latitude, Double longitude, boolean sold,
                            List<String> images, Set<DeliveryType> deliveryOptions) {
        Product product = new Product();
        product.setOwner(owner);
        product.setTitle(title);
        product.setBrand(brand);
        product.setCategory(category);
        product.setSize(size);
        product.setColor(color);
        product.setCondition(condition);
        product.setPrice(new BigDecimal(price));
        product.setOriginalPrice(new BigDecimal(originalPrice));
        product.setShippingFee(new BigDecimal(shippingFee));
        product.setDescription(description);
        product.setReason(reason);
        product.setSellerLocation(sellerLocation);
        product.setLatitude(latitude);
        product.setLongitude(longitude);
        product.setSold(sold);
        product.getImages().addAll(images);
        product.getDeliveryOptions().addAll(deliveryOptions);
        return product;
    }
}
