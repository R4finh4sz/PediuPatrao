package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuarioServiceTest {
    @org.junit.jupiter.api.AfterEach void limparContexto() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test void naoPermiteAlterarProprioPerfilOuLogin() {
        Usuario atual = new Usuario(); atual.setId("admin"); atual.setUsername("admin"); atual.setRole("ADMIN");
        when(usuarios.findById("admin")).thenReturn(Optional.of(atual));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin", "senha"));
        Usuario entrada = new Usuario(); entrada.setUsername("admin"); entrada.setRole("GERENTE");
        assertThrows(IllegalArgumentException.class, () -> service.atualizarUsuario("admin", entrada));
        entrada.setRole("ADMIN"); entrada.setUsername("outro");
        assertThrows(IllegalArgumentException.class, () -> service.atualizarUsuario("admin", entrada));
        verify(usuarios, never()).save(any());
    }

    @Test void ultimoAdminNaoPodeSerExcluidoOuRebaixado() {
        Usuario atual = new Usuario(); atual.setId("a1"); atual.setUsername("admin"); atual.setRole("ADMIN");
        when(usuarios.findById("a1")).thenReturn(Optional.of(atual)); when(usuarios.countByRole("ADMIN")).thenReturn(1L);
        Usuario entrada = new Usuario(); entrada.setUsername("admin"); entrada.setRole("GERENTE");
        assertThrows(IllegalArgumentException.class, () -> service.atualizarUsuario("a1", entrada));
        assertThrows(IllegalArgumentException.class, () -> service.deletarUsuario("a1"));
        verify(usuarios, never()).save(any()); verify(usuarios, never()).deleteById(any());
    }

    @Test void podeExcluirOutroAdminSeAindaExisteUm() {
        Usuario atual = new Usuario(); atual.setId("a1"); atual.setUsername("outro"); atual.setRole("ADMIN");
        when(usuarios.findById("a1")).thenReturn(Optional.of(atual)); when(usuarios.countByRole("ADMIN")).thenReturn(2L);
        service.deletarUsuario("a1");
        var ordem = inOrder(usuarios);
        ordem.verify(usuarios).bloquearAdministradores(); ordem.verify(usuarios).findById("a1");
        ordem.verify(usuarios).countByRole("ADMIN"); ordem.verify(usuarios).deleteById("a1");
    }
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UsuarioService service = new UsuarioService(usuarios, encoder);

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "GERENTE", "ATENDENTE"})
    void autenticacaoUsaPerfilPersistido(String perfil) {
        Usuario usuario = new Usuario();
        usuario.setUsername("teste");
        usuario.setPassword(encoder.encode("senha"));
        usuario.setRole(perfil);
        when(usuarios.findByUsername("teste")).thenReturn(Optional.of(usuario));
        var detalhes = new UsuarioDetailsService(usuarios).loadUserByUsername("teste");
        assertTrue(detalhes.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + perfil)));
    }

    @Test
    void atualizacaoSemSenhaMantemHashExistente() {
        Usuario atual = new Usuario();
        atual.setId("u1");
        atual.setPassword(encoder.encode("senha"));
        String hash = atual.getPassword();
        when(usuarios.findById("u1")).thenReturn(Optional.of(atual));
        Usuario entrada = new Usuario();
        entrada.setUsername("teste");
        entrada.setRole("GERENTE");
        entrada.setPassword("");
        service.atualizarUsuario("u1", entrada);
        assertEquals(hash, atual.getPassword());
        assertEquals("GERENTE", atual.getRole());
    }

    @Test
    void criacaoRejeitaPerfilInvalido() {
        Usuario entrada = new Usuario();
        entrada.setUsername("teste");
        entrada.setRole("USER");
        entrada.setPassword("senha");
        assertThrows(IllegalArgumentException.class, () -> service.salvarUsuario(entrada));
        verify(usuarios, never()).save(any());
    }
}
