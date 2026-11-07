package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface UsuarioRepository extends MongoRepository<Usuario, String> {
    Optional<Usuario> findByUsername(String username);
    long countByRole(String role);

    // Serializa remoções/rebaixamentos de administradores entre instâncias na mesma transação.
    @org.springframework.data.mongodb.repository.Query("{'role': 'ADMIN'}")
    @org.springframework.data.mongodb.repository.Update("{'$inc': {'revisaoAdministracao': 1}}")
    long bloquearAdministradores();
}
