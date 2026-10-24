package com.bte.service.escrow;
import com.bte.entity.Registration;
import java.math.*;
import java.util.*;
public final class SquadPayoutAllocator {
    private SquadPayoutAllocator(){}
    public record Allocation(int memberIndex,Long userId,BigDecimal amount){}
    public static List<Allocation> split(Registration registration,BigDecimal amount) {
        var members=registration.getSquadMembers();
        boolean legacy=members==null || members.isEmpty() || members.stream().anyMatch(m -> m.getWalletUserId()==null);
        if(legacy)return List.of(new Allocation(0,registration.getOwnerUserId(),amount.setScale(2,RoundingMode.UNNECESSARY)));
        long cents=amount.movePointRight(2).longValueExact();long base=cents/members.size();long remainder=cents%members.size();
        var result=new ArrayList<Allocation>();
        for(int i=0;i<members.size();i++) result.add(new Allocation(i,i==0?registration.getOwnerUserId():members.get(i).getWalletUserId(),BigDecimal.valueOf(base+(i<remainder?1:0),2)));
        return result;
    }
}
