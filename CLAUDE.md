# kekitemkekifalta — regras do projeto

App Android pessoal de um casal, todo em pt-BR. kekitem = o que tem em casa; kekifalta = o que falta.
O dono não programa: trabalho entregue por PR, e só está pronto com o CI verde.

## Estrutura
- `core/`: Kotlin puro. Regras de domínio (duração adaptativa, estados, rota, catálogo, texto da lista, formato do backup). Regra nova vai aqui, com teste.
- `app/`: Android (Compose M3, Room, WorkManager, Navigation). DI manual: `AppContainer` em `KekitemApp.kt`.
- Telas em `app/.../ui/<tela>/`, ViewModel no mesmo arquivo. Escritas que antecedem navegação usam `appScope`.

## Contratos de dados
- Toda tabela tem `id` (UUID texto), `householdId`, `updatedAt`, `deletedAt`. Exclusão é lógica; nunca apagar dado do usuário (exceto "Substituir" no import).
- Acesso a dados só pelas interfaces de `data/` (ItemRepository, MarketRepository, BackupRepository, SettingsStore).
- `items.stockedAt` espelha o ciclo aberto em `item_cycles`: mudar um exige mudar o outro (ver RoomItemRepository).
- Valores salvos (`Sector.key`, `ClockType.key`, `Side`, `EndReason`) nunca mudam.
- Room: NUNCA migração destrutiva. Mudou entidade: subir `DB_VERSION`, escrever a Migration N→N+1 em `Migrations.ALL` e commitar o schema novo de `app/schemas/` (o CI falha sem isso).
- Backup JSON (`core/Backup.kt`): só adicionar campos com default; nunca renomear nem remover. Import mescla por id (vence o maior `updatedAt`).

## Build e entrega
- Versões só em `gradle/libs.versions.toml`. AGP 9 tem Kotlin embutido: não aplicar `org.jetbrains.kotlin.android`.
- Keystore fixo em `keystore/` (repo privado, uso pessoal). Nunca trocar: o APK novo deixaria de instalar por cima.
- `versionCode` = número da execução do workflow Release. Não renomear `release.yml`.
- `ci.yml`: testes, APK release e monkey no emulador. `release.yml`: push na `main` publica a release `latest`.
- O ambiente do Claude não tem Android SDK nem Maven do Google: validar pelo Actions no branch. Screenshots do teste de fumaça: `git fetch origin +refs/ci/smoke-shots:refs/ci/smoke-shots`.

## Estilo
- Texto de interface em pt-BR, tom bem-humorado; código e comentários em inglês.
- Visual "adesivo": `StickerCard`, cores de `KekTheme.colors`. Texto sobre fundos `*Soft` usa `ink`.
- Ícones são vector drawables próprios (`res/drawable/ic_*.xml`); fontes Fredoka e Nunito (OFL).
