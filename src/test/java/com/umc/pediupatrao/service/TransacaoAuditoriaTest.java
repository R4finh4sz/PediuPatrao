package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.*;
import com.umc.pediupatrao.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransacaoAuditoriaTest {
    static class Transacoes extends AbstractPlatformTransactionManager {
        boolean rollback, commit;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object t, org.springframework.transaction.TransactionDefinition d) {}
        protected void doCommit(DefaultTransactionStatus s) { commit = true; }
        protected void doRollback(DefaultTransactionStatus s) { rollback = true; }
    }

    @Test void falhaDeAuditoriaMarcaOperacaoParaRollback() {
        var pedidos = mock(PedidoRepository.class);
        var produtos = mock(ProdutoRepository.class);
        var clientes = mock(ClienteRepository.class);
        var auditoria = mock(AuditoriaService.class);
        var transacoes = new Transacoes();
        var proxy = new ProxyFactory(new PedidoService(pedidos, produtos, clientes, auditoria));
        proxy.addAdvice(new TransactionInterceptor(transacoes, new AnnotationTransactionAttributeSource()));
        var service = (PedidoService) proxy.getProxy();
        Pedido pedido = new Pedido(); pedido.setId("p1"); pedido.setStatus("RECEBIDO");
        when(pedidos.findById("p1")).thenReturn(Optional.of(pedido));
        when(pedidos.save(any())).thenAnswer(i -> i.getArgument(0));
        doThrow(new IllegalStateException("Mongo indisponível")).when(auditoria)
                .registrar(anyString(), anyString(), anyString(), anyMap(), anyMap(), any());
        assertThrows(IllegalStateException.class, () -> service.atualizarStatus("p1", "EM_PREPARACAO"));
        assertTrue(transacoes.rollback); assertFalse(transacoes.commit);
        var ordem = inOrder(pedidos, auditoria);
        ordem.verify(pedidos).findById("p1"); ordem.verify(pedidos).save(any());
        ordem.verify(auditoria).registrar(anyString(), anyString(), anyString(), anyMap(), anyMap(), any());
    }

    @Test void todasOperacoesAuditadasTemFronteiraTransacional() throws Exception {
        var source = new AnnotationTransactionAttributeSource();
        for (var classe : List.of(PedidoService.class, ClienteService.class)) {
            var nomes = classe == PedidoService.class
                    ? Set.of("criarPedido", "atualizarStatus", "aplicarDesconto", "cancelar")
                    : Set.of("novoCliente", "salvar", "excluir");
            for (var metodo : classe.getMethods()) if (nomes.contains(metodo.getName())) {
                assertNotNull(source.getTransactionAttribute(metodo, classe), metodo.getName());
            }
        }
    }
}
