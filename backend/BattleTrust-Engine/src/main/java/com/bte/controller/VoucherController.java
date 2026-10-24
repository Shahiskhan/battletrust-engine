package com.bte.controller;
import com.bte.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
@RestController @RequiredArgsConstructor
public class VoucherController {
    private final VoucherService service;
    @GetMapping("/api/v1/public/registrations/{id}/voucher")
    public VoucherService.VoucherResponse get(@PathVariable Long id, Authentication auth, @RequestHeader(value = "X-Registration-Ticket", required = false) String ticket) { return service.get(id, auth, ticket); }
    @GetMapping("/api/v1/organizer/tournaments/{id}/vouchers")
    public java.util.List<VoucherService.InventoryItem> inventory(@PathVariable Long id,Authentication auth) {return service.inventory(id,auth);}
    @GetMapping("/api/v1/organizer/tournaments/{id}/vouchers/{voucherId}/qr")
    public java.util.Map<String,String> qr(@PathVariable Long id,@PathVariable String voucherId,Authentication auth) {return service.inventoryQr(id,voucherId,auth);}
}
