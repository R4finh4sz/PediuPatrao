package com.umc.pediupatrao.controller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@ControllerAdvice
public class TratamentoErros {
 @ExceptionHandler(org.springframework.transaction.TransactionException.class)
 @ResponseStatus(HttpStatus.CONFLICT)
 public Object transacao(HttpServletRequest request,Model model) {
  String mensagem="A operação não foi confirmada. Atualize a página e tente novamente.";
  if(request.getRequestURI().startsWith("/api/")) return ResponseEntity.status(409).body(java.util.Map.of("erro",mensagem));
  model.addAttribute("message",mensagem); model.addAttribute("status",409); return "error";
 }
 @ExceptionHandler(org.springframework.dao.DataAccessException.class)
 @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
 public Object banco(HttpServletRequest request,Model model) {
  String mensagem="Não foi possível acessar os dados. Tente novamente em instantes.";
  if(request.getRequestURI().startsWith("/api/")) return ResponseEntity.status(503).body(java.util.Map.of("erro",mensagem));
  model.addAttribute("message",mensagem); model.addAttribute("status",503); return "error";
 }
 @ModelAttribute("perfil")
 public String perfil(org.springframework.security.core.Authentication auth) {
  return auth==null?"":auth.getAuthorities().stream().findFirst().map(a->a.getAuthority().replace("ROLE_","")).orElse("");
 }
 @ExceptionHandler(IllegalArgumentException.class)
 @ResponseStatus(HttpStatus.BAD_REQUEST)
 public Object invalido(IllegalArgumentException erro,HttpServletRequest request,Model model) {
  if(request.getRequestURI().startsWith("/api/")) return ResponseEntity.badRequest().body(java.util.Map.of("erro",erro.getMessage()));
  model.addAttribute("message",erro.getMessage()); model.addAttribute("status",400); return "error";
 }
 @ExceptionHandler(OptimisticLockingFailureException.class)
 @ResponseStatus(HttpStatus.CONFLICT)
 public Object conflito(HttpServletRequest request,Model model) {
  String mensagem="Pedido alterado por outro usuário. Atualize a página e tente novamente.";
  if(request.getRequestURI().startsWith("/api/")) return ResponseEntity.status(409).body(java.util.Map.of("erro",mensagem));
  model.addAttribute("message",mensagem); model.addAttribute("status",409); return "error";
 }
}
