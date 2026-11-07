package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PedidoService {
    private final PedidoRepository pedidos;
    private final ProdutoRepository produtos;
    private final ClienteRepository clientes;
    private final AuditoriaService auditoria;

    public PedidoService(PedidoRepository pedidos, ProdutoRepository produtos,
            ClienteRepository clientes, AuditoriaService auditoria) {
        this.pedidos = pedidos;
        this.produtos = produtos;
        this.clientes = clientes;
        this.auditoria = auditoria;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE')")
    @org.springframework.transaction.annotation.Transactional
    public Pedido criarPedido(Pedido entrada) {
        if (entrada.getClienteId() == null || !clientes.existsById(entrada.getClienteId())) {
            throw new IllegalArgumentException("Cliente não encontrado.");
        }
        if (entrada.getItens() == null || entrada.getItens().isEmpty()) {
            throw new IllegalArgumentException("Informe os itens do pedido.");
        }

        Pedido pedido = new Pedido();
        pedido.setClienteId(entrada.getClienteId());
        List<ItemPedido> itens = new ArrayList<>();
        for (ItemPedido item : entrada.getItens()) {
            if (item == null || item.getProdutoId() == null || item.getQuantidade() <= 0) {
                throw new IllegalArgumentException("Produto e quantidade positiva são obrigatórios.");
            }
            Produto produto = produtos.findById(item.getProdutoId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
            if (!Boolean.TRUE.equals(produto.getAtivo()) || produto.getPreco() == null
                    || !Double.isFinite(produto.getPreco()) || produto.getPreco() < 0) {
                throw new IllegalArgumentException("Produto indisponível ou preço inválido.");
            }
            ItemPedido salvo = new ItemPedido();
            salvo.setProdutoId(produto.getId());
            salvo.setNome(produto.getNome());
            salvo.setQuantidade(item.getQuantidade());
            salvo.setPrecoUnitario(BigDecimal.valueOf(produto.getPreco())
                    .setScale(2, RoundingMode.HALF_UP));
            itens.add(salvo);
        }
        pedido.setItens(itens);
        pedido.setStatus("RECEBIDO");
        pedido.setEntrada(Instant.now());
        pedido.setResponsavelRegistro(usuario());
        calcular(pedido);
        pedido = pedidos.save(pedido);
        auditoria.registrar("INCLUSAO_PEDIDO", "Pedido", pedido.getId(),
                Map.of(), dados(pedido), null);
        return pedido;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidos.findAll();
    }

    @PreAuthorize("hasRole('GERENTE')")
    @org.springframework.transaction.annotation.Transactional
    public Pedido atualizarStatus(String id, String status) {
        Pedido pedido = buscar(id);
        List<String> proximos = switch (pedido.getStatus() == null ? "" : pedido.getStatus()) {
            case "RECEBIDO" -> List.of("EM_PREPARACAO");
            case "EM_PREPARACAO", "EM_PREPARO" -> List.of("PRONTO");
            case "PRONTO" -> List.of("SAIU_PARA_ENTREGA", "RETIRADO");
            case "SAIU_PARA_ENTREGA", "RETIRADO" -> List.of("FINALIZADO");
            default -> List.of();
        };
        if (!proximos.contains(status)) {
            throw new IllegalArgumentException("Transição de status inválida.");
        }
        var antes = dados(pedido);
        pedido.setStatus(status);
        if (status.equals("SAIU_PARA_ENTREGA") || status.equals("RETIRADO")) {
            pedido.setSaida(Instant.now());
            pedido.setResponsavelSaida(usuario());
        }
        return salvar(pedido, antes, "ALTERACAO_STATUS", null);
    }

    @PreAuthorize("hasRole('GERENTE')")
    @org.springframework.transaction.annotation.Transactional
    public Pedido aplicarDesconto(String id, BigDecimal percentual) {
        Pedido pedido = buscar(id);
        validarAntesSaida(pedido);
        if (percentual == null || percentual.compareTo(BigDecimal.ZERO) < 0
                || percentual.compareTo(new BigDecimal("20")) > 0) {
            throw new IllegalArgumentException("Desconto deve estar entre 0 e 20%.");
        }
        var antes = dados(pedido);
        pedido.setDescontoPercentual(percentual);
        calcular(pedido);
        return salvar(pedido, antes, "DESCONTO_PEDIDO", null);
    }

    @PreAuthorize("hasRole('GERENTE')")
    @org.springframework.transaction.annotation.Transactional
    public Pedido cancelar(String id, String justificativa) {
        Pedido pedido = buscar(id);
        validarAntesSaida(pedido);
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("Justificativa é obrigatória.");
        }
        var antes = dados(pedido);
        pedido.setStatus("CANCELADO");
        pedido.setJustificativaCancelamento(justificativa.trim());
        return salvar(pedido, antes, "CANCELAMENTO_PEDIDO", justificativa.trim());
    }

    private Pedido buscar(String id) {
        return pedidos.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
    }

    private void validarAntesSaida(Pedido pedido) {
        if (pedido.getSaida() != null || !List.of("RECEBIDO", "EM_PREPARACAO", "EM_PREPARO", "PRONTO")
                .contains(pedido.getStatus() == null ? "" : pedido.getStatus())) {
            throw new IllegalArgumentException("Operação permitida somente antes da saída em pedido ativo.");
        }
    }

    private void calcular(Pedido pedido) {
        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            throw new IllegalArgumentException("Pedido antigo sem preços registrados não permite desconto.");
        }
        BigDecimal subtotal = pedido.getItens().stream()
                .map(item -> item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fator = BigDecimal.ONE.subtract(
                pedido.getDescontoPercentual().divide(new BigDecimal("100")));
        pedido.setValorTotal(subtotal.multiply(fator).setScale(2, RoundingMode.HALF_UP));
    }

    private Pedido salvar(Pedido pedido, Map<String, Object> antes,
            String operacao, String justificativa) {
        Pedido salvo = pedidos.save(pedido);
        auditoria.registrar(operacao, "Pedido", salvo.getId(), antes, dados(salvo), justificativa);
        return salvo;
    }

    private String usuario() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private Map<String, Object> dados(Pedido pedido) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("clienteId", pedido.getClienteId());
        dados.put("itens", pedido.getItens());
        dados.put("status", pedido.getStatus());
        dados.put("descontoPercentual", pedido.getDescontoPercentual());
        dados.put("valorTotal", pedido.getValorTotal());
        dados.put("entrada", pedido.getEntrada());
        dados.put("saida", pedido.getSaida());
        dados.put("responsavelRegistro", pedido.getResponsavelRegistro());
        dados.put("responsavelSaida", pedido.getResponsavelSaida());
        dados.put("justificativaCancelamento", pedido.getJustificativaCancelamento());
        return dados;
    }
}
