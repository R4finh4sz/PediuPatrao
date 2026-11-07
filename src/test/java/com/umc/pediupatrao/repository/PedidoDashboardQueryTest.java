package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PedidoDashboardQueryTest {
    @Test void intervaloUsaAmbosLimitesNaMesmaExpressaoDeEntrada() throws Exception {
        var conversoes = new MongoCustomConversions(List.of());
        var contexto = new MongoMappingContext();
        contexto.setSimpleTypeHolder(conversoes.getSimpleTypeHolder());
        contexto.afterPropertiesSet();
        var conversor = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, contexto);
        conversor.setCustomConversions(conversoes);
        conversor.setCodecRegistryProvider(com.mongodb.MongoClientSettings::getDefaultCodecRegistry);
        conversor.afterPropertiesSet();
        var mongo = mock(MongoOperations.class, RETURNS_DEEP_STUBS);
        when(mongo.getConverter()).thenReturn(conversor);
        when(mongo.execute(any(org.springframework.data.mongodb.core.DbCallback.class)))
                .thenReturn(com.mongodb.MongoClientSettings.getDefaultCodecRegistry());
        when(mongo.getCollectionName(Pedido.class)).thenReturn("pedidos");
        var repository = new MongoRepositoryFactory(mongo).getRepository(PedidoRepository.class);
        var inicio = Instant.parse("2026-11-07T03:00:00Z");
        var fim = Instant.parse("2026-11-08T03:00:00Z");

        repository.findByEntradaGreaterThanEqualAndEntradaLessThan(inicio, fim);

        var consulta = ArgumentCaptor.forClass(Query.class);
        verify(mongo.query(Pedido.class).as(Pedido.class)).matching(consulta.capture());
        var filtro = consulta.getValue().getQueryObject();
        assertEquals(1, filtro.size());
        var limites = (Document) filtro.get("entrada");
        assertEquals(2, limites.size());
        assertEquals(java.util.Date.from(inicio), limites.get("$gte"));
        assertEquals(java.util.Date.from(fim), limites.get("$lt"));
    }
}
