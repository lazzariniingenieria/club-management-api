package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.CreateMemberRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.MemberResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateMemberRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.entity.MemberStatus;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicateDniException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.mapper.MemberMapper;
import com.lazzariniingenieria.clubmanagementapi.mapper.MemberMapperImpl;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final Long CLUB_ID = 1L;
    private static final Long MEMBER_ID = 10L;
    private static final String DNI = "30111222";
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDate JOINED_AT = LocalDate.parse("2026-01-01");

    @Mock
    private MemberRepository memberRepository;

    private final MemberMapper memberMapper = new MemberMapperImpl();

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, memberMapper);
    }

    @Test
    void shouldCreateMemberWhenDniIsNotTaken() {
        CreateMemberRequest request =
                new CreateMemberRequest("Marcos", "Gomez", DNI, "+54 11 4444-5555", "marcos@example.com");
        when(memberRepository.existsByClubIdAndDni(CLUB_ID, DNI)).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenReturn(member());

        MemberResponse response = memberService.createMember(CLUB_ID, request);

        ArgumentCaptor<Member> savedMemberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(savedMemberCaptor.capture());
        Member savedMember = savedMemberCaptor.getValue();

        assertThat(savedMember.getClubId()).isEqualTo(CLUB_ID);
        assertThat(savedMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(savedMember.getFirstName()).isEqualTo("Marcos");
        assertThat(savedMember.getFamilyGroupId()).isNull();
        assertThat(response.id()).isEqualTo(MEMBER_ID);
        assertThat(response.dni()).isEqualTo(DNI);
        assertThat(response.email()).isEqualTo("marcos@example.com");
        assertThat(response.status()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void shouldThrowDuplicateDniWhenCreatingWithDniAlreadyUsedInClub() {
        CreateMemberRequest request = new CreateMemberRequest("Marcos", "Gomez", DNI, null, null);
        when(memberRepository.existsByClubIdAndDni(CLUB_ID, DNI)).thenReturn(true);

        assertThatThrownBy(() -> memberService.createMember(CLUB_ID, request)).isInstanceOf(DuplicateDniException.class);

        verify(memberRepository, never()).save(any());
    }

    @Test
    void shouldReturnAllMembersForClub() {
        when(memberRepository.findByClubIdOrderByCreatedAtDesc(CLUB_ID)).thenReturn(List.of(member()));

        List<MemberResponse> response = memberService.listMembers(CLUB_ID);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(MEMBER_ID);
    }

    @Test
    void shouldReturnMemberWhenFound() {
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member()));

        MemberResponse response = memberService.getMember(CLUB_ID, MEMBER_ID);

        assertThat(response.id()).isEqualTo(MEMBER_ID);
    }

    @Test
    void shouldThrowMemberNotFoundWhenGettingMissingMember() {
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMember(CLUB_ID, MEMBER_ID)).isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void shouldUpdateMemberProfileWhenDniIsAvailable() {
        Member existingMember = member();
        UpdateMemberRequest request =
                new UpdateMemberRequest("Marcos", "Gomez", "30999888", "+54 11 4444-5555", "new@example.com");
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(existingMember));
        when(memberRepository.existsByClubIdAndDniAndIdNot(CLUB_ID, "30999888", MEMBER_ID)).thenReturn(false);
        when(memberRepository.save(existingMember)).thenReturn(existingMember);

        MemberResponse response = memberService.updateMember(CLUB_ID, MEMBER_ID, request);

        assertThat(response.dni()).isEqualTo("30999888");
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.familyGroupId()).isEqualTo(1L);
    }

    @Test
    void shouldUpdateMemberProfileWhenDniRemainsUnchanged() {
        Member existingMember = member();
        UpdateMemberRequest request =
                new UpdateMemberRequest("Marcos", "Gomez", DNI, "+54 11 4444-5555", "updated@example.com");
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(existingMember));
        when(memberRepository.existsByClubIdAndDniAndIdNot(CLUB_ID, DNI, MEMBER_ID)).thenReturn(false);
        when(memberRepository.save(existingMember)).thenReturn(existingMember);

        MemberResponse response = memberService.updateMember(CLUB_ID, MEMBER_ID, request);

        assertThat(response.dni()).isEqualTo(DNI);
        assertThat(response.email()).isEqualTo("updated@example.com");
        assertThat(response.familyGroupId()).isEqualTo(1L);
    }

    @Test
    void shouldThrowDuplicateDniWhenUpdatingToDniUsedByAnotherMember() {
        Member existingMember = member();
        UpdateMemberRequest request =
                new UpdateMemberRequest("Marcos", "Gomez", "30999888", "+54 11 4444-5555", "new@example.com");
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(existingMember));
        when(memberRepository.existsByClubIdAndDniAndIdNot(CLUB_ID, "30999888", MEMBER_ID)).thenReturn(true);

        assertThatThrownBy(() -> memberService.updateMember(CLUB_ID, MEMBER_ID, request))
                .isInstanceOf(DuplicateDniException.class);

        verify(memberRepository, never()).save(any());
    }

    @Test
    void shouldThrowMemberNotFoundWhenUpdatingMissingMember() {
        UpdateMemberRequest request =
                new UpdateMemberRequest("Marcos", "Gomez", "30999888", "+54 11 4444-5555", "new@example.com");
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.updateMember(CLUB_ID, MEMBER_ID, request))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void shouldDeactivateMemberWhenFound() {
        Member existingMember = member();
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(existingMember));
        when(memberRepository.save(existingMember)).thenReturn(existingMember);

        MemberResponse response = memberService.deactivateMember(CLUB_ID, MEMBER_ID);

        assertThat(existingMember.getStatus()).isEqualTo(MemberStatus.INACTIVE);
        assertThat(response.status()).isEqualTo(MemberStatus.INACTIVE);
    }

    @Test
    void shouldThrowMemberNotFoundWhenDeactivatingMissingMember() {
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.deactivateMember(CLUB_ID, MEMBER_ID))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void shouldReactivateMemberWhenFound() {
        Member existingMember = member();
        existingMember.setStatus(MemberStatus.INACTIVE);
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(existingMember));
        when(memberRepository.save(existingMember)).thenReturn(existingMember);

        MemberResponse response = memberService.reactivateMember(CLUB_ID, MEMBER_ID);

        assertThat(existingMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.status()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void shouldThrowMemberNotFoundWhenReactivatingMissingMember() {
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.reactivateMember(CLUB_ID, MEMBER_ID))
                .isInstanceOf(MemberNotFoundException.class);
    }

    private Member member() {
        return Member.builder()
                .id(MEMBER_ID)
                .clubId(CLUB_ID)
                .familyGroupId(1L)
                .firstName("Marcos")
                .lastName("Gomez")
                .dni(DNI)
                .phone("+54 11 4444-5555")
                .email("marcos@example.com")
                .joinedAt(JOINED_AT)
                .status(MemberStatus.ACTIVE)
                .createdAt(CREATED_AT)
                .build();
    }
}
