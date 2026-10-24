package com.bte.controller;
import com.bte.service.FinancialPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
@RestController @RequiredArgsConstructor
public class FinancialPolicyController {
    private final FinancialPolicyService service;
    @GetMapping("/api/v1/public/settings/finance") public java.util.Map<String,Object> get() { var p=service.get();return java.util.Map.of("platformFeePercent",p.getPlatformFeePercent(),"automaticPayoutsEnabled",p.isAutomaticPayoutsEnabled(),"version",p.getVersion()); }
    @GetMapping("/api/v1/admin/settings/finance") public com.bte.entity.FinancialPolicy admin() { return service.get(); }
    @PutMapping("/api/v1/admin/settings/finance") public com.bte.entity.FinancialPolicy update(@Valid @RequestBody FinancialPolicyService.Update request,Authentication auth) {return service.update(request,auth.getName());}
}
