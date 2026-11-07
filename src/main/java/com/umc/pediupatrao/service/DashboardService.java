package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
public class DashboardService {
    public static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    public static final List<String> STATUS = List.of("RECEBIDO", "EM_PREPARACAO", "PRONTO",
            "SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO", "CANCELADO");
    private final PedidoRepository pedidos;

    public DashboardService(PedidoRepository pedidos) { this.pedidos = pedidos; }

    public record Dia(LocalDate data, BigDecimal faturamento, long pedidos) {}
    public record ProdutoVendido(String produtoId, String nome, long quantidade) {}
    public record Resumo(LocalDate inicio, LocalDate fim, String status, String fusoHorario,
            BigDecimal faturamento, long totalPedidos, long finalizados, long cancelados,
            BigDecimal ticketMedio, long semValor, List<Dia> vendas,
            Map<String, Long> situacao, List<ProdutoVendido> produtos) {}

    public Resumo resumo(LocalDate inicio, LocalDate fim, String status) {
        if (fim == null) fim = LocalDate.now(ZONA);
        if (inicio == null) inicio = fim.minusDays(29);
        if (inicio.isAfter(fim) || ChronoUnit.DAYS.between(inicio, fim) > 365) {
            throw new IllegalArgumentException("Informe um período de até 366 dias com início anterior ou igual ao fim.");
        }
        if (status != null && !STATUS.contains(status)) {
            throw new IllegalArgumentException("Status inválido.");
        }
        var lista = pedidos.findByEntradaGreaterThanEqualAndEntradaLessThan(
                inicio.atStartOfDay(ZONA).toInstant(), fim.plusDays(1).atStartOfDay(ZONA).toInstant());
        var situacao = new LinkedHashMap<String, Long>();
        STATUS.forEach(s -> situacao.put(s, 0L));
        var valores = new TreeMap<LocalDate, BigDecimal>();
        var contagens = new HashMap<LocalDate, Long>();
        inicio.datesUntil(fim.plusDays(1)).forEach(d -> valores.put(d, BigDecimal.ZERO));
        var ranking = new HashMap<String, ProdutoVendido>();
        BigDecimal faturamento = BigDecimal.ZERO;
        long total = 0, finalizados = 0, cancelados = 0, semValor = 0, vendasValidas = 0;
        for (Pedido p : lista) {
            String estado = "EM_PREPARO".equals(p.getStatus()) ? "EM_PREPARACAO" : p.getStatus();
            if (status != null && !status.equals(estado)) continue;
            total++;
            situacao.merge(estado == null ? "SEM_STATUS" : estado, 1L, Long::sum);
            if ("CANCELADO".equals(estado)) cancelados++;
            if (!"FINALIZADO".equals(estado)) continue;
            finalizados++;
            if (p.getValorTotal() == null) { semValor++; continue; }
            vendasValidas++;
            faturamento = faturamento.add(p.getValorTotal());
            LocalDate dia = p.getEntrada().atZone(ZONA).toLocalDate();
            valores.merge(dia, p.getValorTotal(), BigDecimal::add);
            contagens.merge(dia, 1L, Long::sum);
            if (p.getItens() == null) continue;
            for (var item : p.getItens()) {
                if (item == null || item.getProdutoId() == null || item.getQuantidade() <= 0) continue;
                ranking.compute(item.getProdutoId(), (id, anterior) -> new ProdutoVendido(id,
                        item.getNome() == null ? "Produto sem nome" : item.getNome(),
                        item.getQuantidade() + (anterior == null ? 0 : anterior.quantidade())));
            }
        }
        var vendas = valores.entrySet().stream().map(e -> new Dia(e.getKey(), e.getValue(),
                contagens.getOrDefault(e.getKey(), 0L))).toList();
        var produtos = ranking.values().stream().sorted(Comparator.comparingLong(ProdutoVendido::quantidade)
                .reversed().thenComparing(ProdutoVendido::produtoId)).limit(10).toList();
        return new Resumo(inicio, fim, status, ZONA.getId(), faturamento, total, finalizados, cancelados,
                vendasValidas == 0 ? BigDecimal.ZERO : faturamento.divide(BigDecimal.valueOf(vendasValidas), 2,
                RoundingMode.HALF_UP), semValor, vendas, situacao, produtos);
    }
}
