package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, DashboardService.class})
class DashboardControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PedidoRepository pedidos;
    @MockitoBean UsuarioDetailsService usuarios;

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "GERENTE"})
    void painelEApiDisponiveisParaGestao(String perfil) throws Exception {
        when(pedidos.findByEntradaGreaterThanEqualAndEntradaLessThan(any(), any())).thenReturn(List.of());
        mvc.perform(get("/dashboard").with(user("gestor").roles(perfil))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("@coreui/coreui@5.3.1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
        mvc.perform(get("/api/dashboard/resumo?inicio=2026-11-01&fim=2026-11-07").with(user("gestor").roles(perfil)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPedidos").value(0))
                .andExpect(jsonPath("$.vendas.length()").value(7));
    }

    @Test void atendenteNaoConsultaPainelNemApi() throws Exception {
        mvc.perform(get("/dashboard").with(user("a").roles("ATENDENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/resumo").with(user("a").roles("ATENDENTE"))).andExpect(status().isForbidden());
        verifyNoInteractions(pedidos);
    }

    @Test void filtrosInvalidosRetornam400() throws Exception {
        mvc.perform(get("/api/dashboard/resumo?inicio=2026-11-07&fim=2026-11-01").with(user("g").roles("GERENTE")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").exists());
        mvc.perform(get("/api/dashboard/resumo?inicio=ontem").with(user("g").roles("GERENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test void falhaNoBancoRetorna503SemExporDetalhes() throws Exception {
        when(pedidos.findByEntradaGreaterThanEqualAndEntradaLessThan(any(), any()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("detalhe interno"));
        mvc.perform(get("/api/dashboard/resumo").with(user("g").roles("GERENTE")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.erro").value("Não foi possível acessar os dados. Tente novamente em instantes."));
    }
}
