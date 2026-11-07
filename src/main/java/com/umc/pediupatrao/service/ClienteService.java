package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClienteService {
    private final ClienteRepository clientes;
    private final AuditoriaService auditoria;

    public ClienteService(ClienteRepository clientes, AuditoriaService auditoria) {
        this.clientes = clientes;
        this.auditoria = auditoria;
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    @org.springframework.transaction.annotation.Transactional
    public Cliente novoCliente(Cliente cliente) {
        return salvar(cliente);
    }

    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE')")
    public List<Cliente> listarClientes() {
        return clientes.findAll();
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    @org.springframework.transaction.annotation.Transactional
    public void excluir(String id) {
        Cliente cliente = clientes.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado."));
        clientes.deleteById(id);
        auditoria.registrar("EXCLUSAO_CLIENTE", "Cliente", id, dados(cliente), Map.of(), null);
    }

    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clientes.findById(id);
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    @org.springframework.transaction.annotation.Transactional
    public Cliente salvar(Cliente cliente) {
        if (cliente.getNome() == null || cliente.getNome().isBlank()
                || cliente.getTelefone() == null || cliente.getTelefone().isBlank()) {
            throw new IllegalArgumentException("Nome e telefone são obrigatórios.");
        }
        if (cliente.getId() != null && cliente.getId().isBlank()) {
            cliente.setId(null);
        }
        Map<String, Object> antes = cliente.getId() == null ? Map.of()
                : dados(clientes.findById(cliente.getId()).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado.")));
        Cliente salvo = clientes.save(cliente);
        auditoria.registrar(antes.isEmpty() ? "INCLUSAO_CLIENTE" : "ALTERACAO_CLIENTE",
                "Cliente", salvo.getId(), antes, dados(salvo), null);
        return salvo;
    }

    private Map<String, Object> dados(Cliente cliente) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("nome", cliente.getNome());
        dados.put("telefone", cliente.getTelefone());
        dados.put("cep", cliente.getCep());
        dados.put("logradouro", cliente.getLogradouro());
        dados.put("numero", cliente.getNumero());
        dados.put("complemento", cliente.getComplemento());
        dados.put("bairro", cliente.getBairro());
        dados.put("cidade", cliente.getCidade());
        dados.put("estado", cliente.getEstado());
        dados.put("endereco", cliente.getEndereco());
        return dados;
    }
}
