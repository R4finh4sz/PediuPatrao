package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.*;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardServiceTest {
    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final DashboardService service = new DashboardService(pedidos);
    private final LocalDate dia = LocalDate.of(2026, 11, 7);

    private Pedido pedido(String status, String total) {
        Pedido p = new Pedido(); p.setStatus(status); p.setEntrada(Instant.parse("2026-11-07T03:00:00Z"));
        p.setValorTotal(total == null ? null : new BigDecimal(total));
        ItemPedido item = new ItemPedido(); item.setProdutoId("pizza"); item.setNome("Pizza"); item.setQuantidade(2);
        p.setItens(List.of(item)); return p;
    }

    @Test void calculaVendasSemCanceladosOuValoresAusentesEPreencheDiasVazios() {
        when(pedidos.findByEntradaGreaterThanEqualAndEntradaLessThan(any(), any())).thenReturn(List.of(
                pedido("FINALIZADO", "40.50"), pedido("FINALIZADO", "20.00"),
                pedido("CANCELADO", "100"), pedido("RECEBIDO", "90"), pedido("FINALIZADO", null)));
        var r = service.resumo(dia, dia.plusDays(1), null);
        assertEquals(new BigDecimal("60.50"), r.faturamento());
        assertEquals(new BigDecimal("30.25"), r.ticketMedio());
        assertEquals(5, r.totalPedidos()); assertEquals(3, r.finalizados());
        assertEquals(1, r.cancelados()); assertEquals(1, r.semValor());
        assertEquals(4, r.produtos().getFirst().quantidade());
        assertEquals(BigDecimal.ZERO, r.vendas().get(1).faturamento());
        verify(pedidos).findByEntradaGreaterThanEqualAndEntradaLessThan(
                Instant.parse("2026-11-07T03:00:00Z"), Instant.parse("2026-11-09T03:00:00Z"));
    }

    @Test void filtroNormalizaStatusAntigo() {
        when(pedidos.findByEntradaGreaterThanEqualAndEntradaLessThan(any(), any()))
                .thenReturn(List.of(pedido("EM_PREPARO", "30"), pedido("FINALIZADO", "40")));
        var r = service.resumo(dia, dia, "EM_PREPARACAO");
        assertEquals(1, r.totalPedidos()); assertEquals(1L, r.situacao().get("EM_PREPARACAO"));
        assertEquals(BigDecimal.ZERO, r.faturamento()); assertTrue(r.produtos().isEmpty());
    }

    @Test void rejeitaFiltrosInvalidosAntesDeConsultarBanco() {
        assertThrows(IllegalArgumentException.class, () -> service.resumo(dia.plusDays(1), dia, null));
        assertThrows(IllegalArgumentException.class, () -> service.resumo(dia.minusDays(366), dia, null));
        assertThrows(IllegalArgumentException.class, () -> service.resumo(dia, dia, "INVALIDO"));
        verifyNoInteractions(pedidos);
    }

    @Test void vazioTemZeroSemDivisaoPorZero() {
        when(pedidos.findByEntradaGreaterThanEqualAndEntradaLessThan(any(), any())).thenReturn(List.of());
        var r = service.resumo(dia, dia, null);
        assertEquals(BigDecimal.ZERO, r.ticketMedio()); assertEquals(0, r.totalPedidos());
        assertEquals(1, r.vendas().size()); assertTrue(r.produtos().isEmpty());
    }
}
