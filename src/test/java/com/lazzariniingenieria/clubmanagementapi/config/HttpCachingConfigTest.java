package com.lazzariniingenieria.clubmanagementapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lazzariniingenieria.clubmanagementapi.controller.MemberController;
import com.lazzariniingenieria.clubmanagementapi.dto.MemberResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.MemberStatus;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import com.lazzariniingenieria.clubmanagementapi.service.AccountSessionService;
import com.lazzariniingenieria.clubmanagementapi.service.MemberService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, HttpCachingConfig.class})
class HttpCachingConfigTest {

    private static final Long CLUB_ID = 1L;
    private static final String MEMBERS_PATH = "/api/members";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AccountSessionService accountSessionService;

    @Test
    void shouldSendAnEtagWithASuccessfulJsonResponse() throws Exception {
        when(memberService.listMembers(CLUB_ID)).thenReturn(List.of(memberResponse("Marcos")));

        mockMvc.perform(get(MEMBERS_PATH).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG));
    }

    @Test
    void shouldAnswerNotModifiedWithoutBodyWhenTheResponseDidNotChange() throws Exception {
        when(memberService.listMembers(CLUB_ID)).thenReturn(List.of(memberResponse("Marcos")));
        String eTag = eTagOfMembers();

        mockMvc.perform(get(MEMBERS_PATH).with(asAdmin()).header(HttpHeaders.IF_NONE_MATCH, eTag))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }

    @Test
    void shouldSendTheNewBodyWhenTheResponseChangedSinceTheEtag() throws Exception {
        when(memberService.listMembers(CLUB_ID)).thenReturn(List.of(memberResponse("Marcos")));
        String previousETag = eTagOfMembers();
        when(memberService.listMembers(CLUB_ID)).thenReturn(List.of(memberResponse("Marta")));

        String newETag = mockMvc.perform(get(MEMBERS_PATH).with(asAdmin()).header(HttpHeaders.IF_NONE_MATCH, previousETag))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);

        assertThat(newETag).isNotEqualTo(previousETag);
    }

    @Test
    void shouldAskClientsToRevalidatePrivatelyInsteadOfForbiddingStorage() throws Exception {
        when(memberService.listMembers(CLUB_ID)).thenReturn(List.of(memberResponse("Marcos")));

        mockMvc.perform(get(MEMBERS_PATH).with(asAdmin()))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-cache"));
    }

    @Test
    void shouldNotSendAnEtagWithARejectedRequest() throws Exception {
        mockMvc.perform(get(MEMBERS_PATH).with(asMember()))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG));
    }

    private String eTagOfMembers() throws Exception {
        return mockMvc.perform(get(MEMBERS_PATH).with(asAdmin()))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);
    }

    private MemberResponse memberResponse(String firstName) {
        return new MemberResponse(10L, firstName, "Gomez", "30111222", null, null, null, LocalDate.parse("2026-01-01"),
                MemberStatus.ACTIVE, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"), 1L, 1L);
    }

    private RequestPostProcessor asAdmin() {
        AuthenticatedUser principal = new AuthenticatedUser(2L, CLUB_ID, UserRole.ADMIN, null);

        return authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private RequestPostProcessor asMember() {
        AuthenticatedUser principal = new AuthenticatedUser(3L, CLUB_ID, UserRole.MEMBER, 5L);

        return authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
