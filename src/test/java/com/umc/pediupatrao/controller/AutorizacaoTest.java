package com.umc.pediupatrao.controller;
import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.*;
import com.umc.pediupatrao.repository.*;
import com.umc.pediupatrao.service.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
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
@WebMvcTest({PedidoController.class,UsuarioController.class,ClienteController.class,AuditoriaController.class,HomeController.class})
@Import({SecurityConfig.class,UsuarioDetailsService.class,PedidoService.class,ClienteService.class,UsuarioService.class,AuditoriaService.class,ProdutoService.class})
class AutorizacaoTest {
 @ParameterizedTest @ValueSource(strings={"ADMIN","GERENTE","ATENDENTE"})
 void edicaoProdutoCarregaESalvaProdutoExistente(String perfil) throws Exception {
  Produto produto=new Produto(); produto.setId("p1"); produto.setNome("Pizza original"); produto.setTipo("Pizza"); produto.setPreco(30.0); produto.setAtivo(true);
  when(produtos.findById("p1")).thenReturn(Optional.of(produto));
  when(produtos.existsById("p1")).thenReturn(true);
  when(produtos.save(any())).thenAnswer(i->i.getArgument(0));
  mvc.perform(get("/produtos/editar/p1").with(user("usuario").roles(perfil))).andExpect(status().isOk())
   .andExpect(content().string(org.hamcrest.Matchers.containsString("Pizza original")))
   .andExpect(content().string(org.hamcrest.Matchers.containsString("type=\"checkbox\"")))
   .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
  mvc.perform(post("/produtos/salvar").with(user("usuario").roles(perfil)).with(csrf())
   .param("id","p1").param("nome","Pizza alterada").param("tipo","Pizza").param("preco","35.50").param("_ativo","on"))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/produtos"));
  verify(produtos).save(argThat(p->"p1".equals(p.getId()) && "Pizza alterada".equals(p.getNome())
   && Double.valueOf(35.50).equals(p.getPreco()) && Boolean.FALSE.equals(p.getAtivo())));
 }
 @Test void editarProdutoInexistenteRetorna404() throws Exception {
  mvc.perform(get("/produtos/editar/inexistente").with(user("gerente").roles("GERENTE"))).andExpect(status().isNotFound());
 }
 @ParameterizedTest @ValueSource(strings={"ATIVO","INATIVO","SEM_STATUS"})
 void listaProdutosAceitaStatusAtivoAusente(String estado) throws Exception {
  Produto produto=new Produto(); produto.setId("produto1"); produto.setNome("Pizza teste"); produto.setPreco(30.0);
  produto.setAtivo("SEM_STATUS".equals(estado)?null:"ATIVO".equals(estado));
  when(produtos.findAll()).thenReturn(List.of(produto));
  mvc.perform(get("/produtos").with(user("gerente").roles("GERENTE"))).andExpect(status().isOk())
   .andExpect(content().string(org.hamcrest.Matchers.containsString("Pizza teste")))
   .andExpect(content().string(org.hamcrest.Matchers.containsString("ATIVO".equals(estado)?"<td>Sim</td>":"<td>Não</td>")));
 }
 @Autowired MockMvc mvc;
 @MockitoBean PedidoRepository pedidos;
 @MockitoBean ProdutoRepository produtos;
 @MockitoBean ClienteRepository clientes;
 @MockitoBean UsuarioRepository usuarios;
 @MockitoBean AuditoriaRepository auditoria;
 @BeforeEach void preparar() {
  when(pedidos.findAll()).thenReturn(List.of()); when(clientes.findAll()).thenReturn(List.of());
  when(produtos.findAll()).thenReturn(List.of()); when(auditoria.findAllByOrderByDataHoraDesc()).thenReturn(List.of());
 }
 @ParameterizedTest @ValueSource(strings={"ADMIN","ATENDENTE"})
 void somenteGerentePodeDescontarECancelar(String role) throws Exception {
  mvc.perform(put("/api/pedidos/p1/desconto").param("percentual","10").with(user("usuario").roles(role)).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(put("/api/pedidos/p1/cancelamento").param("justificativa","Motivo").with(user("usuario").roles(role)).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(put("/api/pedidos/p1/status").param("status","PRONTO").with(user("usuario").roles(role)).with(csrf())).andExpect(status().isForbidden());
  verifyNoInteractions(pedidos);
 }
 @Test void gerentePodeDescontarEPersistirAuditoria() throws Exception {
  Pedido p=new Pedido(); p.setId("p1"); p.setStatus("RECEBIDO"); p.setValorTotal(new BigDecimal("50"));
  ItemPedido item=new ItemPedido(); item.setProdutoId("produto"); item.setQuantidade(1); item.setPrecoUnitario(new BigDecimal("50")); p.setItens(List.of(item));
  when(pedidos.findById("p1")).thenReturn(Optional.of(p)); when(pedidos.save(any())).thenAnswer(i->i.getArgument(0));
  mvc.perform(put("/api/pedidos/p1/desconto").param("percentual","20").with(user("gerente").roles("GERENTE")).with(csrf()))
   .andExpect(status().isOk()).andExpect(jsonPath("$.valorTotal").value(40));
  verify(auditoria).insert(argThat(e->e.getUsuario().equals("gerente") && e.getPerfil().equals("GERENTE") && e.getOperacao().equals("DESCONTO_PEDIDO")));
 }
 @ParameterizedTest @ValueSource(strings={"GERENTE","ATENDENTE"})
 void cadastroEPerfisSaoExclusivosDoAdmin(String role) throws Exception {
  mvc.perform(post("/api/usuarios").with(user("usuario").roles(role)).with(csrf()).contentType("application/json")
   .content("{\"username\":\"invasor\",\"password\":\"senha\",\"role\":\"ADMIN\"}")).andExpect(status().isForbidden());
  mvc.perform(put("/api/usuarios/id").with(user("usuario").roles(role)).with(csrf()).contentType("application/json")
   .content("{\"username\":\"invasor\",\"role\":\"ADMIN\"}")).andExpect(status().isForbidden());
  mvc.perform(get("/usuarios/novo").with(user("usuario").roles(role))).andExpect(status().isForbidden());
  verifyNoInteractions(usuarios);
 }
 @Test void cadastroAnonimoNaoPermitido() throws Exception {
  mvc.perform(post("/api/usuarios").with(csrf()).contentType("application/json")
   .content("{\"username\":\"invasor\",\"password\":\"senha\",\"role\":\"ADMIN\"}")).andExpect(status().is3xxRedirection());
 }
 @Test void csrfObrigatorioNasApisEFormularios() throws Exception {
  mvc.perform(put("/api/pedidos/p1/desconto").param("percentual","10").with(user("gerente").roles("GERENTE"))).andExpect(status().isForbidden());
  mvc.perform(post("/usuarios/salvar").param("username","novo").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
  verifyNoInteractions(pedidos,usuarios);
 }
 @Test void atendenteNaoConsultaAuditoria() throws Exception {
  mvc.perform(get("/api/auditoria").with(user("atendente").roles("ATENDENTE"))).andExpect(status().isForbidden());
  mvc.perform(get("/auditoria").with(user("atendente").roles("ATENDENTE"))).andExpect(status().isForbidden());
 }
 @ParameterizedTest @ValueSource(strings={"ADMIN","GERENTE"})
 void consultaAuditoriaRestritaESemAlteracao(String role) throws Exception {
  mvc.perform(get("/api/auditoria").with(user("usuario").roles(role))).andExpect(status().isOk());
  mvc.perform(delete("/api/auditoria/id").with(user("usuario").roles(role)).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(put("/api/auditoria/id").with(user("usuario").roles(role)).with(csrf())).andExpect(status().isNotFound());
 }
 @Test void gerenteConsultaClienteMasNaoAltera() throws Exception {
  mvc.perform(get("/api/clientes").with(user("gerente").roles("GERENTE"))).andExpect(status().isOk());
  mvc.perform(put("/api/clientes/c1").with(user("gerente").roles("GERENTE")).with(csrf()).contentType("application/json")
   .content("{\"nome\":\"Cliente\",\"telefone\":\"999\"}")).andExpect(status().isForbidden());
 }
 @Test void adminNaoRegistraPedidos() throws Exception {
  for(String role:List.of("ADMIN")) {
   mvc.perform(post("/api/pedidos").with(user("usuario").roles(role)).with(csrf()).contentType("application/json")
    .content("{\"clienteId\":\"c1\",\"itens\":[]}")).andExpect(status().isForbidden());
  }
 }
 @Test void gerentePodeRegistrarPedidoPelaApi() throws Exception {
  when(clientes.existsById("c1")).thenReturn(true);
  Produto produto=new Produto(); produto.setId("p1"); produto.setNome("Pizza"); produto.setAtivo(true); produto.setPreco(25.0);
  when(produtos.findById("p1")).thenReturn(Optional.of(produto));
  when(pedidos.save(any())).thenAnswer(i->{Pedido p=i.getArgument(0);p.setId("novo");return p;});
  mvc.perform(post("/api/pedidos").with(user("gerente").roles("GERENTE")).with(csrf()).contentType("application/json")
   .content("{\"clienteId\":\"c1\",\"itens\":[{\"produtoId\":\"p1\",\"quantidade\":2}]}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.valorTotal").value(50));
 }
 @Test void senhasNaoSaoExpostas() throws Exception {
  Usuario u=new Usuario(); u.setId("u1"); u.setUsername("atendente"); u.setRole("ATENDENTE"); u.setPassword("hash-secreto");
  when(usuarios.findAll()).thenReturn(List.of(u));
  mvc.perform(get("/api/usuarios").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].password").doesNotExist());
 }
 @ParameterizedTest @ValueSource(strings={"ADMIN","GERENTE","ATENDENTE"})
 void paginaPedidosRenderizaParaTresPerfis(String role) throws Exception {
  mvc.perform(get("/pedidos").with(user("usuario").roles(role))).andExpect(status().isOk()).andExpect(view().name("layout"));
 }
 @Test void formularioDeUsuarioNaoExibeHashEIncluiCsrf() throws Exception {
  Usuario u=new Usuario(); u.setId("u1"); u.setUsername("teste"); u.setRole("ATENDENTE"); u.setPassword("hash-secreto");
  when(usuarios.findById("u1")).thenReturn(Optional.of(u));
  mvc.perform(get("/usuarios/editar/u1").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
   .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hash-secreto"))))
   .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
 }
 @Test void paginaAuditoriaRenderizaValoresAnterioresENovos() throws Exception {
  Auditoria e=new Auditoria(); e.setCampos(Map.of("logradouro",new Auditoria.Alteracao("Rua antiga","Rua nova")));
  when(auditoria.findAllByOrderByDataHoraDesc()).thenReturn(List.of(e));
  mvc.perform(get("/auditoria").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
   .andExpect(content().string(org.hamcrest.Matchers.containsString("Rua antiga")))
   .andExpect(content().string(org.hamcrest.Matchers.containsString("Rua nova")));
 }
 @ParameterizedTest @ValueSource(strings={"ADMIN","GERENTE","ATENDENTE"})
 void paginaPedidosRenderizaItensEFormularios(String role) throws Exception {
  Pedido p=new Pedido(); p.setId("p1"); p.setClienteId("c1"); p.setStatus("PRONTO"); p.setValorTotal(new BigDecimal("50.00"));
  ItemPedido item=new ItemPedido(); item.setNome("Pizza"); item.setQuantidade(1); item.setPrecoUnitario(new BigDecimal("50.00")); p.setItens(List.of(item));
  when(pedidos.findAll()).thenReturn(List.of(p));
  mvc.perform(get("/pedidos").with(user("usuario").roles(role))).andExpect(status().isOk())
   .andExpect(content().string(org.hamcrest.Matchers.containsString("Pizza")));
 }
 @Test void regrasInvalidasRetornamErro400NaApi() throws Exception {
  Pedido p=new Pedido(); p.setId("p1"); p.setStatus("RECEBIDO");
  when(pedidos.findById("p1")).thenReturn(Optional.of(p));
  mvc.perform(put("/api/pedidos/p1/status").param("status","FINALIZADO").with(user("gerente").roles("GERENTE")).with(csrf()))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("Transição de status inválida."));
 }
 @Test void atendenteAlteraClienteEAuditoriaCapturaValores() throws Exception {
  Cliente antes=new Cliente(); antes.setId("c1"); antes.setNome("Maria"); antes.setTelefone("1111");
  when(clientes.findById("c1")).thenReturn(Optional.of(antes));
  when(clientes.save(any())).thenAnswer(i->i.getArgument(0));
  mvc.perform(put("/api/clientes/c1").with(user("atendente").roles("ATENDENTE")).with(csrf()).contentType("application/json")
   .content("{\"nome\":\"Maria\",\"telefone\":\"2222\"}")).andExpect(status().isOk());
  verify(auditoria).insert(argThat(e->e.getCampos().get("telefone").anterior().equals("1111") && e.getCampos().get("telefone").novo().equals("2222")));
 }
}
