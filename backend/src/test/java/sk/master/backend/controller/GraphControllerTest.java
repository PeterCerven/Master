package sk.master.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sk.master.backend.persistence.dto.GraphDto;
import sk.master.backend.persistence.dto.GraphSummaryDto;
import sk.master.backend.persistence.entity.UserEntity;
import sk.master.backend.persistence.model.Role;
import sk.master.backend.persistence.repository.UserRepository;
import sk.master.backend.service.auth.JwtService;
import sk.master.backend.service.construct.GraphConstructionService;
import sk.master.backend.service.util.FileService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GraphController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class GraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GraphConstructionService graphConstructionService;

    @MockitoBean
    private FileService fileService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private static final Authentication ALICE =
            new UsernamePasswordAuthenticationToken("alice@x", "pw",
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));

    private UserEntity stubUser(Long id, String email) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        user.setName("test");
        user.setPassword("hash");
        user.setRole(Role.USER);
        user.setEnabled(true);
        return user;
    }

    @Test
    void listGraphs_returnsServiceSummariesAsJson() throws Exception {
        when(userRepository.findByEmail("alice@x"))
                .thenReturn(Optional.of(stubUser(7L, "alice@x")));
        when(graphConstructionService.listUserGraphs(7L))
                .thenReturn(List.of(new GraphSummaryDto(1L, "g1", LocalDateTime.now(), 5, 4, 2)));

        mockMvc.perform(get("/api/graph/list").principal(ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("g1"))
                .andExpect(jsonPath("$[0].nodeCount").value(5))
                .andExpect(jsonPath("$[0].edgeCount").value(4))
                .andExpect(jsonPath("$[0].stationCount").value(2));
    }

    @Test
    void saveGraph_passesDtoToServiceAndReturnsSummary() throws Exception {
        when(userRepository.findByEmail("alice@x"))
                .thenReturn(Optional.of(stubUser(7L, "alice@x")));
        when(graphConstructionService.saveGraphToDatabase(any(GraphDto.class), any(), anyString(), eq(7L)))
                .thenReturn(new GraphSummaryDto(99L, "my-graph", LocalDateTime.now(), 2, 1, 0));

        String requestJson = """
                {
                  "name": "my-graph",
                  "graph": {
                    "nodes": [
                      {"id": "n0", "lat": 48.0,   "lon": 17.0,   "componentId": 0},
                      {"id": "n1", "lat": 48.001, "lon": 17.001, "componentId": 0}
                    ],
                    "edges": [
                      {"sourceId": "n0", "targetId": "n1", "distanceMeters": 100.0}
                    ],
                    "metrics": null
                  },
                  "stations": []
                }
                """;

        mockMvc.perform(post("/api/graph/save")
                        .principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.name").value("my-graph"));

        verify(graphConstructionService).saveGraphToDatabase(any(GraphDto.class), any(), eq("my-graph"), eq(7L));
    }
}
