package com.bte.service;
import com.bte.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InviteReservationTest {
 @Test void captainInviteReservesFullSquad(){var r=Registration.builder().tournament(Tournament.builder().squadSize(4).build()).squadInviteToken(java.util.UUID.randomUUID().toString()).squadMembers(java.util.List.of(new SquadMember("Captain","uid"))).build();assertEquals(4,r.reservedPlayers());}
 @Test void historicalContractKeepsActualRoster(){var r=Registration.builder().tournament(Tournament.builder().squadSize(4).build()).squadMembers(java.util.List.of(new SquadMember("Captain","uid"))).build();assertEquals(1,r.reservedPlayers());}
 @Test void qrContainsAnImage(){String value=new InviteQrService().image("https://example.com/squads/join/test");assertTrue(value.startsWith("data:image/png;base64,"));assertTrue(java.util.Base64.getDecoder().decode(value.substring(value.indexOf(',')+1)).length>100);}
}
