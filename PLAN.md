# PLAN — kekitemkekifalta

## Fase 1: app completo em um aparelho

### Feito
- [x] Projeto Gradle (AGP 9.4, Kotlin 2.4.20, Compose BOM 2026.09.00, Room 2.8.5, WorkManager 2.12, Navigation 2.10)
- [x] Módulo `core` em Kotlin puro com testes unitários
- [x] Itens: nome, setor, quantidade/unidade, observação, relógio (estraga/acaba), duração
- [x] Catálogo embutido (~180 itens de casa brasileira) com apelidos para a busca
- [x] Adição rápida com autocompletar; item fora do catálogo criado na hora no setor escolhido
- [x] Kekitem: busca, filtro por setor, ordenação "acaba primeiro" / A–Z
- [x] Ciclos (entrada/saída) e duração adaptativa (média em escala log, amortece outliers, converge em 3–4 ciclos)
- [x] Quantidade comprada escala a duração (itens que "acabam")
- [x] Estados tem / acabando (75%) / provavelmente acabou (100%)
- [x] "Estragou" registra desperdício e reduz a estimativa; edição manual da duração
- [x] Kekifalta: lista manual + "Sugeridos" (confirmar / ainda tem)
- [x] Desfazer em toda movimentação (snackbar)
- [x] "Começar com o básico" no kekitem vazio
- [x] Mercados com corredores ordenados, setores por corredor, reordenação arrastando, modelo padrão
- [x] Anotação de prateleira por item e por mercado (e corredor fixo opcional)
- [x] Mapa esquemático: blocos na ordem do trajeto, contagem, corredores vazios recolhidos, corredor atual em destaque
- [x] Modo mercado: tela sempre acesa, alvos grandes, barra de progresso, concluir compra
- [x] Aprendizado de rota: pergunta ao final se a ordem real divergiu (nunca altera sozinho)
- [x] Exportar lista em texto (compartilhar e copiar), por corredor ou por setor
- [x] Lista em PDF (compartilhar e imprimir)
- [x] Backup completo em JSON: exportar e importar (mesclar ou substituir)
- [x] Resumo diário local em horário configurável (no máximo 1 por dia)
- [x] Tela de desperdício por mês
- [x] Identidade própria (visual "adesivo", Fredoka/Nunito, ícones próprios), tema claro/escuro, ícone do app
- [x] CI: testes + APK + teste de fumaça (monkey) no emulador; Release: APK assinado na release `latest`
- [x] Keystore fixo versionado; versionCode automático

### Pendências desta fase
- [ ] Criar o branch `main` (o repositório estava vazio) e trocar o branch padrão para `main`

### Para depois (fora do escopo da fase 1)
- [ ] Sincronização entre os dois aparelhos (dados já prontos: UUID, householdId, updatedAt, deletedAt)
- [ ] Login e backend
- [ ] Leitor de código de barras e leitura de nota fiscal
- [ ] Integração com MacroDroid, intents e atalhos externos
- [ ] Widget de tela inicial
- [ ] Preços e histórico de preços

### Ideias anotadas no caminho
- [ ] Ajustar a quantidade comprada direto no modo mercado
- [ ] Teste instrumentado de migração do Room quando existir a versão 2 do banco
- [ ] Deduplicar itens com mesmo nome ao mesclar backups de aparelhos diferentes
