package com.bte.service.escrow;
import com.bte.entity.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SquadPayoutAllocatorTest {
    Registration squad(){var members=new ArrayList<SquadMember>();for(long i=1;i<=4;i++){var m=new SquadMember("Player "+i,"UID"+i);m.setWalletUserId(i);members.add(m);}return Registration.builder().ownerUserId(10L).squadMembers(members).build();}
    @Test void splitsIntoFourIndividualWalletsAndUsesCurrentCaptainOwner(){var p=SquadPayoutAllocator.split(squad(),new BigDecimal("370.00"));assertEquals(4,p.size());assertEquals(10L,p.getFirst().userId());assertTrue(p.stream().allMatch(a -> a.amount().equals(new BigDecimal("92.50"))));}
    @Test void centRemaindersAreConservedDeterministically(){var p=SquadPayoutAllocator.split(squad(),new BigDecimal("0.07"));assertEquals(List.of(new BigDecimal("0.02"),new BigDecimal("0.02"),new BigDecimal("0.02"),new BigDecimal("0.01")),p.stream().map(SquadPayoutAllocator.Allocation::amount).toList());assertEquals(new BigDecimal("0.07"),p.stream().map(SquadPayoutAllocator.Allocation::amount).reduce(BigDecimal.ZERO,BigDecimal::add));}
    @Test void legacyEntriesRetainExistingCaptainPayoutContract(){var r=Registration.builder().ownerUserId(8L).squadMembers(List.of(new SquadMember("Legacy","UID"))).build();var p=SquadPayoutAllocator.split(r,new BigDecimal("10.00"));assertEquals(1,p.size());assertEquals(8L,p.getFirst().userId());}
}
