# Tenant Beneficiary Manager

Pequeno sistema de cadastro para uma plataforma que atende múltiplos clientes (tenants):
um registro global de **Pessoas** (compartilhado entre todos os tenants) e um registro de
**Beneficiários** por tenant, que vincula uma Pessoa a um tenant com atributos próprios
(matrícula, tipo, status, data de adesão).

## Como executar

Pré-requisitos: Docker e Docker Compose. Nenhuma outra ferramenta é necessária — não há passo
manual de banco de dados, migração ou seed.

```bash
docker compose up
```

Isso sobe três serviços:

- **db** — PostgreSQL 16, com um healthcheck que os demais serviços aguardam.
- **backend** — Spring Boot, na porta `8080`. As migrações Liquibase (schema + dados de
  demonstração) rodam automaticamente na inicialização.
- **frontend** — build estático do Vue 3 servido por Nginx, na porta `8081`, que faz proxy reverso
  de `/api`, `/swagger-ui.html` e `/v3/api-docs` para o backend.

Acesse a aplicação em `http://localhost:8081` e a documentação da API (Swagger UI, gerada a
partir do código pelo springdoc-openapi) em `http://localhost:8080/swagger-ui.html`.

### Usuários de demonstração (seed)

Os nomes seguem um padrão previsível (`Tenant N`, `User N - PAPEL`, `Pessoa N`,
`Beneficiário N - Tenant M`) para deixar claro, ao olhar a tela, se um registro deveria ou não
aparecer para o usuário logado.

| Usuário                 | Senha     | Tenants             | Papel                             |
|-------------------------|-----------|---------------------|-----------------------------------|
| `User 3 - ADMIN`        | `demo123` | nenhum              | System Admin (plataforma inteira) |
| `User 1 - NORMAL`       | `demo123` | Tenant 1 e Tenant 2 | Normal em ambos                   |
| `User 2 - TENANT ADMIN` | `demo123` | Tenant 1            | Tenant Admin do Tenant 1          |
| `User 4 - TENANT ADMIN` | `demo123` | Tenant 3            | Tenant Admin do Tenant 3          |
| `User 5 - NORMAL`       | `demo123` | Tenant 3 e Tenant 4 | Normal em ambos                   |
| `User 6 - NORMAL`       | `demo123` | Tenant 2            | Normal                            |

#### Seed de demonstração é opcional

Por padrão (`docker compose up`, sem nenhum passo extra) os dados acima são inseridos
automaticamente (perfil Spring `demo`). Para subir a aplicação com o banco totalmente migrado mas
**sem** nenhum dado de demonstração, use o perfil `no-demo`:

```bash
SPRING_PROFILES_ACTIVE=no-demo docker compose up
```

Mesmo nesse modo, é possível entrar no sistema — veja "Criando a primeira conta" abaixo.

#### Criando a primeira conta

A tela `/criar-conta` (acessível sem login) é o ponto de entrada para uma instância sem nenhum
usuário: a primeira conta criada por ela é automaticamente promovida a System Admin, sem
nenhuma escolha de papel — é a única opção que faz sentido quando ainda não existe nenhum Tenant.
A partir da segunda conta em diante, toda conta criada por essa tela nasce com o papel mais
simples (Normal, sem vínculo a nenhum Tenant); elevar esse papel exige uma ação de um
administrador já existente através das capacidades administrativas já existentes (conceder
System Admin ou Tenant Admin, adicionar a um Tenant) — nunca através da própria tela de criação de
conta, mesmo que a requisição tente sugerir isso.

### Rodando os testes localmente (sem Docker Compose)

```bash
cd backend && mvn test        # requer um Docker acessível (Testcontainers sobe um Postgres real)
cd frontend && npm install && npm test
```

Ambos os comandos também geram um relatório de cobertura de testes a cada execução — sem nunca
falhar o build por causa dele, é só informativo:

- Backend (JaCoCo): `backend/target/site/jacoco/index.html`
- Frontend (v8/Vitest): `frontend/coverage/index.html`

## Decisões arquiteturais

### Isolamento multitenant

Estratégia: **banco único, coluna discriminadora, com o filtro aplicado pelo próprio banco**. Um
único PostgreSQL guarda tudo. As tabelas globais (`pessoa`, `tenant`, `app_user`,
`user_tenant_membership`) não têm coluna de tenant; Beneficiário é a única entidade tenant-scoped,
e a aplicação nunca lê sua tabela base (`beneficiario`) diretamente — ela enxerga a view
`vw_beneficiario`, que filtra por uma variável de sessão do Postgres. Um filtro central
(`TenantContextFilter`) resolve qual tenant está ativo em cada requisição:

1. Lê o principal autenticado (JWT, resolvido por `JwtAuthenticationFilter`).
2. Lê o cabeçalho `X-Tenant-Id`.
3. Valida, contra `user_tenant_membership`, que o usuário realmente pertence a esse tenant —
   caso contrário, responde `403` **antes de qualquer acesso a repositório**. Um System Admin é a
   única exceção: pode agir sobre qualquer tenant independentemente de membership, e todo uso
   dessa capacidade é registrado em `tenant_access_audit_log` (quem, quando, qual tenant).
4. Publica o tenant validado em `TenantContext` (um `ThreadLocal` por requisição).

`BeneficiarioService` é o único ponto que lê `TenantContext`, e aplica esse id à sessão do banco
(`TenantSessionContext`, via `SELECT set_config('app.tenant_id', ?, true)`, escopado à transação)
antes de qualquer query. A coluna `tenant_id` da tabela base tem esse mesmo valor como `DEFAULT`,
então um `INSERT` feito através da view (que nunca menciona `tenant_id` — a entidade JPA não tem
esse campo) é automaticamente carimbado com o tenant ativo. Resultado: nenhuma query Java pode
"esquecer" o filtro — mesmo que o código de aplicação tivesse um bug, o próprio banco não
devolveria linhas de outro tenant. Um id de Beneficiário de outro tenant retorna `404` (nunca
`403` ou qualquer confirmação de que o registro existe em outro tenant), evitando IDOR por
enumeração de ids. Se a transação nunca define `app.tenant_id`, a view devolve zero linhas (não um
erro) e um `INSERT` falha por violar o `NOT NULL` de `tenant_id` — nos dois casos, falha fechada,
nunca dados sem filtro.

**Por que não schema-per-tenant ou database-per-tenant?** Ambos dão isolamento físico mais forte,
mas exigem provisionamento de schema/banco por tenant e tornam artificial a exigência de um banco
compartilhado para Pessoa/tenants/usuários — Pessoa precisaria viver em outro lugar compartilhado
de qualquer forma. Para a escala deste sistema (poucos tenants, dezenas/centenas de registros), a
coluna discriminadora — reforçada por uma view em vez de depender só do código de aplicação lembrar
de filtrar — é a opção mais simples que ainda concentra o isolamento em um único ponto auditável.

### Controle de acesso por papéis

Três níveis, aditivos, aplicados via Spring Security (`@PreAuthorize`, com
`@EnableMethodSecurity` habilitado): uma negação de acesso é resolvida na borda, antes do corpo do
método rodar, e é mapeada pelo mesmo `@RestControllerAdvice` para o formato RFC 7807 usado por
qualquer outro erro previsível.

- **System Admin** (`app_user.is_system_admin`, plataforma inteira, independente de qualquer
  membership): CRUD completo de Tenants; concede/revoga o status de System Admin de qualquer
  usuário (inclusive o próprio, exceto quando seria o último — ver abaixo); tem toda ação de
  Tenant Admin e de usuário Normal, em qualquer tenant.
- **Tenant Admin** (`user_tenant_membership.is_tenant_admin`, por membership — um usuário pode
  ser Tenant Admin de um tenant e não de outro): adiciona/remove membros do seu próprio tenant;
  edita o nome do seu próprio tenant; concede/revoga o status de Tenant Admin de outro membro do
  mesmo tenant (inclusive o próprio) — nunca cria nem apaga o Tenant em si, e nunca age fora do
  tenant onde tem esse status.
- **Normal** (padrão de qualquer membership sem status elevado): acessa Pessoa (global) e
  Beneficiário do tenant ativo.

System Admin é expresso como uma authority estática (`hasRole('SYSTEM_ADMIN')`), populada a cada
requisição a partir de uma consulta fresca ao banco — nunca de uma claim do JWT — para que uma
revogação tenha efeito imediato na próxima requisição, sem exigir novo login. Tenant Admin não
pode ser uma authority estática, já que depende de qual tenant está sendo acessado; a expressão
`hasRole('SYSTEM_ADMIN') or @tenantAuthorization.isTenantAdmin(#tenantId)` delega a um bean
pequeno (`TenantAuthorization`) que consulta a membership do tenant específico informado na
requisição.

A proteção "a plataforma nunca fica sem nenhum System Admin" é garantida sob concorrência:
revogar o penúltimo/último System Admin trava (`SELECT ... FOR UPDATE`) o conjunto inteiro de
usuários com esse status antes de recontá-los e escrever, dentro da mesma transação — travar
apenas a linha alvo não bastaria, pois duas revogações concorrentes contra dois administradores
*diferentes* poderiam cada uma travar uma linha distinta e nenhuma bloquear a outra.

### Autenticação simplificada

Login (`POST /api/auth/login`) contra usuários cadastrados, retornando um JWT assinado (HMAC)
com o id do usuário, o username e a lista de tenants aos quais ele pertence. Contas também podem
se auto-cadastrar (`POST /api/auth/register`, ou a tela `/criar-conta`) — a primeira conta já
criada na plataforma vira System Admin automaticamente, e toda conta seguinte nasce como Normal,
sem tenant algum (veja "Criando a primeira conta" acima). O front-end envia
`Authorization: Bearer <token>` e `X-Tenant-Id` em toda chamada; o servidor sempre revalida a
associação usuário↔tenant a cada requisição, nunca confiando apenas no token para decidir *quais*
dados retornar. Sem IdP externo, sem sessão de servidor, sem renovação automática de token (o JWT
expira e força um novo login) — simples o suficiente para autenticar e resolver papel/tenant
ativo, sem construir mais do que isso pede.

### Erros padronizados (RFC 7807)

Todo erro previsível (validação, regra de negócio, conflito, não encontrado, corpo malformado,
acesso negado) passa por um único `@RestControllerAdvice` (`ApiExceptionHandler`) e vira um
`ProblemDetail` (`application/problem+json`), com mensagens em português. Um `500` só ocorre para
falhas realmente inesperadas.

### Documentação da API

`springdoc-openapi` gera o Swagger UI e o JSON OpenAPI diretamente das anotações dos
controllers — não existe um YAML mantido à mão que possa divergir da implementação. `/v3/api-docs`
é sempre a fonte de verdade em runtime.

### Front-end minimalista

Vue 3 (Composition API) + Vite, sem biblioteca de componentes — poucos componentes escritos à
mão, CSS compartilhado mínimo (`src/style.css`), Pinia para estado (autenticação, pessoa,
beneficiário, tenant) e Axios com interceptors que anexam o JWT e o `X-Tenant-Id`
automaticamente. Todo texto voltado ao usuário (rótulos, botões, mensagens de validação/erro)
está em português.

## O que seria feito diferente com mais tempo

- **Renovação/expiração de token**: hoje o JWT expira e força um novo login; um refresh token
  silencioso melhoraria a experiência em sessões longas.
- **Otimização da busca por nome do Beneficiário**: o filtro `pessoaNome` hoje faz um `LIKE`
  case-insensitive via join JPQL; para volumes maiores, um índice funcional
  (`lower(pessoa.nome)`) ou uma coluna denormalizada indexada seria mais eficiente.
- **Auditoria mais rica** (quem criou/alterou cada registro), hoje limitada a
  `created_at`/`updated_at`.
- **Front-end:** Melhorias na interface utilizando uma biblioteca de componentes e Tailwind CSS.
- **Testes E2E** automatizados, para testar casos de uso simulando um usuário real.
- **CI** rodando `mvn test` (com Testcontainers) e `npm test` a cada push, hoje só documentado
  como comando local.
- **Considerar multitenancy híbrida (pool + silo) desde o dia um**: hoje todo tenant convive no
  mesmo banco, discriminado por `tenant_id`. Um modelo mais realista para produção mantém a
  maioria dos tenants em um banco compartilhado ("pool"), mas promove tenants grandes — por
  volume, requisito de compliance, ou isolamento de performance ("noisy neighbor") — para um
  banco dedicado ("silo"), com um terceiro banco só para dados globais não-tenant (Pessoa,
  Tenant, AppUser). Vale decidir isso **antes** de colocar em produção, e não depois, porque
  retrofit é caro nesta base especificamente: (1) o join SQL direto entre `Beneficiario` e
  `Pessoa` (`BeneficiarioRepository.search`) deixa de funcionar assim que os dois vivem em bancos
  diferentes — passaria a exigir join em memória na aplicação; (2) o isolamento por
  `vw_beneficiario`, baseado em uma variável de sessão do Postgres
  (`current_setting('app.tenant_id')`), só existe dentro de uma única conexão/banco — um tenant
  promovido a silo precisaria de um caminho de acesso a dados totalmente diferente.