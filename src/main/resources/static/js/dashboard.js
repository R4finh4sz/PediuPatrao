(() => {
 'use strict';
 const form = document.getElementById('filtros');
 const mensagem = document.getElementById('mensagem');
 const erro = document.getElementById('erro');
 const botao = document.getElementById('atualizar');
 const resultados = document.getElementById('resultados');
 const dinheiro = new Intl.NumberFormat('pt-BR', {style: 'currency', currency: 'BRL'});
 const numero = new Intl.NumberFormat('pt-BR');
 const nomes = {RECEBIDO:'Recebido', EM_PREPARACAO:'Em preparação', PRONTO:'Pronto', SAIU_PARA_ENTREGA:'Saiu para entrega', RETIRADO:'Retirado', FINALIZADO:'Finalizado', CANCELADO:'Cancelado', SEM_STATUS:'Sem situação'};
 const cores = ['#0d6efd','#b45309','#6f42c1','#087f8c','#495057','#198754','#dc3545','#6c757d'];
 const graficos = [];
 function texto(id, valor) { document.getElementById(id).textContent = valor; }
 function linha(valores) {
  const tr = document.createElement('tr');
  valores.forEach(valor => { const td = document.createElement('td'); td.textContent = valor; tr.append(td); });
  return tr;
 }
 function dataLocal(d) { return [d.getUTCFullYear(), String(d.getUTCMonth()+1).padStart(2,'0'), String(d.getUTCDate()).padStart(2,'0')].join('-'); }
 const hoje = new Date(new Intl.DateTimeFormat('en-CA', {timeZone:'America/Sao_Paulo', year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date()) + 'T12:00:00Z');
 form.fim.value = dataLocal(hoje);
 hoje.setUTCDate(hoje.getUTCDate()-29); form.inicio.value = dataLocal(hoje);
 async function atualizar(event) {
  event?.preventDefault();
  if (!form.reportValidity() || botao.disabled) return;
  const parametros = new URLSearchParams(new FormData(form));
  if (!parametros.get('status')) parametros.delete('status');
  botao.disabled = true; resultados.setAttribute('aria-busy','true'); erro.hidden = true;
  mensagem.textContent = 'Atualizando indicadores…';
  try {
   const resposta = await fetch('/api/dashboard/resumo?' + parametros, {headers:{Accept:'application/json'}, cache:'no-store'});
   if (resposta.status === 401 || resposta.redirected) throw new Error('Sua sessão expirou. Entre novamente para consultar o painel.');
   if (!resposta.ok) {
    const detalhe = await resposta.json().catch(() => ({}));
    throw new Error(detalhe.erro || (resposta.status === 403 ? 'Seu perfil não tem acesso ao painel.' : 'Não foi possível consultar os dados. Confira o período e tente atualizar novamente.'));
   }
   const d = await resposta.json();
   texto('faturamento', dinheiro.format(d.faturamento)); texto('ticketMedio', dinheiro.format(d.ticketMedio));
   texto('totalPedidos', numero.format(d.totalPedidos)); texto('concluidos', numero.format(d.finalizados)+' / '+numero.format(d.cancelados));
   document.getElementById('dias').replaceChildren(...d.vendas.map(v => linha([v.data.split('-').reverse().join('/'), numero.format(v.pedidos), dinheiro.format(v.faturamento)])));
   document.getElementById('produtos').replaceChildren(...(d.produtos.length ? d.produtos.map((p,i) => linha([i+1,p.nome,numero.format(p.quantidade)])) : [linha(['—','Nenhum produto vendido neste período.','—'])]));
   document.getElementById('contagens').replaceChildren(...Object.entries(d.situacao).map(([s,n]) => {
    const li = document.createElement('li'); const span = document.createElement('span'); const strong = document.createElement('strong');
    span.textContent = nomes[s] || s; strong.textContent = numero.format(n); li.append(span,strong); return li;
   }));
   graficos.forEach(g => g.destroy()); graficos.length = 0;
   if (typeof Chart !== 'undefined') {
    const options = {responsive:true, maintainAspectRatio:false, animation: false};
    graficos.push(new Chart(document.getElementById('vendas'), {type:'bar', data:{labels:d.vendas.map(v => v.data.split('-').reverse().join('/')), datasets:[{label:'Faturamento (R$)',data:d.vendas.map(v => v.faturamento),backgroundColor:'#0d6efd',borderRadius:3}]}, options:{...options, plugins:{legend:{display:false}}, scales:{y:{beginAtZero:true}}}}));
    graficos.push(new Chart(document.getElementById('situacao'), {type:'doughnut',data:{labels:Object.keys(d.situacao).map(s => nomes[s] || s),datasets:[{data:Object.values(d.situacao),backgroundColor:cores}]}, options:{...options,plugins:{legend:{display:false}}}}));
   }
   mensagem.textContent = (d.totalPedidos ? 'Dados atualizados às '+new Date().toLocaleTimeString('pt-BR')+'.' : 'Nenhum pedido encontrado para estes filtros.') + (d.semValor ? ' '+d.semValor+' pedido(s) finalizado(s) sem valor registrado foram excluídos dos cálculos financeiros e do ranking.' : '') + (typeof Chart === 'undefined' ? ' Gráficos indisponíveis; consulte as tabelas.' : '');
  } catch (e) {
   erro.textContent = e.message; erro.hidden = false;
   mensagem.textContent = 'Atualização não concluída. Os resultados exibidos podem estar desatualizados.';
  } finally { botao.disabled = false; resultados.setAttribute('aria-busy','false'); }
 }
 form.addEventListener('submit', atualizar);
 atualizar();
})();
