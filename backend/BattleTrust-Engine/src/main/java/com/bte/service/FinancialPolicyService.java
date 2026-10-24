package com.bte.service;
import com.bte.entity.FinancialPolicy;
import com.bte.repository.FinancialPolicyRepository;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
@Service @RequiredArgsConstructor
public class FinancialPolicyService {
    private final FinancialPolicyRepository repository;
    public record Update(@NotNull @DecimalMin("0.00") @DecimalMax("99.99") @Digits(integer=2,fraction=2) BigDecimal platformFeePercent, @NotNull Boolean automaticPayoutsEnabled) {}
    @Transactional(readOnly=true) public FinancialPolicy get() { return repository.findById(1L).orElseThrow(); }
    public FinancialPolicy locked() { return repository.locked().orElseThrow(); }
    @Transactional public FinancialPolicy update(Update update, String actor) {
        var policy = locked(); policy.setPlatformFeePercent(update.platformFeePercent()); policy.setAutomaticPayoutsEnabled(update.automaticPayoutsEnabled()); policy.setUpdatedBy(actor); return policy;
    }
}
