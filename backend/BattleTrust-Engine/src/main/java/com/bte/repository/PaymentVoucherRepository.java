package com.bte.repository;
import com.bte.entity.PaymentVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentVoucherRepository extends JpaRepository<PaymentVoucher, String> {
    java.util.List<PaymentVoucher> findByTournamentIdOrderByInventoryNumberAsc(Long id);
    java.util.Optional<PaymentVoucher> findFirstByTournamentIdAndRegistrationIsNullAndStatusOrderByInventoryNumberAsc(Long id, String status);
    java.util.Optional<PaymentVoucher> findByRegistrationId(Long id);
}
