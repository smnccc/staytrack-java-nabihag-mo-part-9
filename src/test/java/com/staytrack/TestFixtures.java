package com.staytrack;

import com.staytrack.domain.Payment;
import com.staytrack.domain.Room;
import com.staytrack.domain.TenantProfile;
import com.staytrack.domain.User;
import com.staytrack.domain.enums.Role;
import com.staytrack.domain.enums.RoomStatus;
import com.staytrack.domain.enums.UserStatus;
import com.staytrack.repository.PaymentRepository;
import com.staytrack.repository.RoomRepository;
import com.staytrack.repository.TenantProfileRepository;
import com.staytrack.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Test-only fixtures. The production {@code DataSeeder} creates nothing but the
 * admin account, so the integration tests get their demo tenant, rooms and
 * unpaid bill from here. This class lives in the test sources and never ships
 * with the application.
 */
@TestConfiguration
public class TestFixtures {

    public static final String DEMO_EMAIL = "alex@staytrack.local";
    public static final String DEMO_PASSWORD = "Tenant@123";

    @Bean
    CommandLineRunner seedTestFixtures(UserRepository users,
                                       RoomRepository rooms,
                                       TenantProfileRepository profiles,
                                       PaymentRepository payments,
                                       PasswordEncoder encoder,
                                       PlatformTransactionManager transactionManager) {
        return args -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (users.existsByEmailIgnoreCase(DEMO_EMAIL)) {
                return;
            }

            Room room304 = saveRoom(rooms, "304", "North Hall", 3, "300.00");
            saveRoom(rooms, "305", "North Hall", 3, "300.00");
            saveRoom(rooms, "401", "North Hall", 4, "320.00");
            saveRoom(rooms, "112", "South Hall", 1, "280.00");

            User tenant = new User();
            tenant.setEmail(DEMO_EMAIL);
            tenant.setFirstName("Alex");
            tenant.setLastName("Rivera");
            tenant.setPhone("+63 912 345 6789");
            tenant.setRole(Role.TENANT);
            tenant.setStatus(UserStatus.ACTIVE);
            tenant.setEmailVerified(true);
            tenant.setPasswordHash(encoder.encode(DEMO_PASSWORD));
            tenant = users.save(tenant);

            TenantProfile profile = new TenantProfile(tenant);
            profile.setRoom(room304);
            profile.setMoveInDate(LocalDate.of(2026, 8, 12));
            profiles.save(profile);

            room304.setStatus(RoomStatus.OCCUPIED);
            rooms.save(room304);

            Payment payment = new Payment();
            payment.setTenant(tenant);
            payment.setRoom(room304);
            payment.setBillingPeriod(LocalDate.now().withDayOfMonth(1));
            payment.setRentAmount(new BigDecimal("300.00"));
            payment.setUtilitiesAmount(new BigDecimal("50.00"));
            payment.setOtherAmount(BigDecimal.ZERO);
            payment.setAmountPaid(BigDecimal.ZERO);
            payment.setDueDate(LocalDate.now().plusDays(3));
            payments.save(payment);
        });
    }

    private Room saveRoom(RoomRepository rooms, String number, String building, Integer floor, String rent) {
        Room room = new Room();
        room.setRoomNumber(number);
        room.setBuilding(building);
        room.setFloor(floor);
        room.setMonthlyRent(new BigDecimal(rent));
        room.setCapacity(2);
        return rooms.save(room);
    }
}
