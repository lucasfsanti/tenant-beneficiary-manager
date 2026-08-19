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

## Decisões arquiteturais

### Isolamento multitenant (Constitution Principle I)

Estratégia: **banco/schema único, coluna discriminadora**. Um único PostgreSQL guarda tudo.
As tabelas globais (`pessoa`, `tenant`, `app_user`, `user_tenant_membership`) não têm coluna de
tenant; a tabela `beneficiario` tem uma coluna `tenant_id`. O tenant ativo de uma requisição é
resolvido **uma única vez**, por um filtro central (`TenantContextFilter`), que:

1. Lê o principal autenticado (JWT, resolvido por `JwtAuthenticationFilter`).
2. Lê o cabeçalho `X-Tenant-Id`.
3. Valida, contra `user_tenant_membership`, que o usuário realmente pertence a esse tenant —
   caso contrário, responde `403` **antes de qualquer acesso a repositório**.
4. Publica o tenant validado em `TenantContext` (um `ThreadLocal` por requisição).

`BeneficiarioService` é o único ponto que lê `TenantContext`, e repassa esse id explicitamente
para cada método de `BeneficiarioRepository` (`findByIdAndTenantId`, `search(tenantId, ...)`,
etc.) — nenhuma query pode "esquecer" o filtro porque o parâmetro é obrigatório na assinatura.
Um id de Beneficiário de outro tenant retorna `404` (nunca `403` ou qualquer confirmação de que o
registro existe em outro tenant), evitando IDOR por enumeração de ids.

**Por que não schema-per-tenant ou database-per-tenant?** Ambos dão isolamento físico mais forte,
mas exigem provisionamento de schema/banco por tenant e tornam artificial a exigência do enunciado
de "um banco global com Pessoa/tenants/usuários" — Pessoa precisaria viver em outro lugar
compartilhado de qualquer forma. Para a escala deste exercício (poucos tenants, dezenas/centenas
de registros), a coluna discriminadora é a opção mais simples que ainda concentra o reforço do
isolamento em um único ponto auditável.

### Controle de acesso por papéis

Três níveis, aditivos, sem novo mecanismo de autorização (sem `@PreAuthorize`) —
seguem o mesmo padrão já usado para regras de negócio: checagem manual na camada
de serviço, lançando uma exceção tipada mapeada para RFC 7807.

- **System Admin** (`app_user.is_system_admin`, plataforma inteira, independente
  de qualquer membership): CRUD completo de Tenants; concede/revoga o status de
  System Admin de qualquer usuário (inclusive o próprio, exceto quando seria o
  último — ver abaixo); tem toda ação de Tenant Admin e de usuário Normal, em
  qualquer tenant.
- **Tenant Admin** (`user_tenant_membership.is_tenant_admin`, por membership —
  um usuário pode ser Tenant Admin de um tenant e não de outro): adiciona/remove
  membros do seu próprio tenant; edita o nome do seu próprio tenant; concede/
  revoga o status de Tenant Admin de outro membro do mesmo tenant (inclusive o
  próprio) — nunca cria nem apaga o Tenant em si, e nunca age fora do tenant
  onde tem esse status.
- **Normal** (padrão de qualquer membership sem status elevado): comportamento
  inalterado desde `001` — Pessoa (global) e Beneficiário (do tenant ativo).

Toda checagem de autorização é resolvida **a cada requisição**, direto do banco
(nunca de uma claim do JWT), pelo mesmo motivo que `TenantContextFilter` já
revalida membership a cada requisição: o status pode mudar durante uma sessão já
autenticada, e o efeito precisa ser imediato, sem exigir novo login.

A proteção "a plataforma nunca fica sem nenhum System Admin" (`FR-011`) é
garantida sob concorrência: revogar o penúltimo/último System Admin trava
(`SELECT ... FOR UPDATE`) o conjunto inteiro de usuários com esse status antes de
recontá-los e escrever, dentro da mesma transação — travar apenas a linha alvo
não bastaria, pois duas revogações concorrentes contra dois administradores
*diferentes* poderiam cada uma travar uma linha distinta e nenhuma bloquear a
outra.

### Autenticação simplificada

Login (`POST /api/auth/login`) contra usuários pré-cadastrados (seed), retornando um JWT
assinado (HMAC) contendo o id do usuário. O front-end envia `Authorization: Bearer <token>` e
`X-Tenant-Id` em toda chamada; o servidor sempre revalida a associação usuário↔tenant a cada
requisição (nunca confia apenas no token para decidir *quais* dados retornar). Sem IdP externo,
sem sessão de servidor — adequado ao escopo pedido ("autenticação simplificada, mas não ausente").

### Erros padronizados (RFC 7807)

Todo erro previsível (validação, regra de negócio, conflito, não encontrado, corpo malformado)
passa por um único `@RestControllerAdvice` (`ApiExceptionHandler`) e vira um `ProblemDetail`
(`application/problem+json`), com mensagens em português. Um `500` só ocorre para falhas
realmente inesperadas.

### Documentação da API

`springdoc-openapi` gera o OpenAPI/Swagger UI diretamente das anotações dos controllers —
não existe um YAML mantido à mão que possa divergir da implementação. O contrato de design-time
usado durante o planejamento está em `specs/001-pessoa-beneficiario-crud/contracts/openapi.yaml`;
a fonte de verdade em runtime é sempre `/v3/api-docs`.

### Front-end minimalista

Vue 3 (Composition API) + Vite, sem biblioteca de componentes — poucos componentes escritos à
mão, CSS compartilhado mínimo (`src/style.css`), Pinia para estado (auth/pessoa/beneficiário) e
Axios com interceptors que anexam o JWT e o `X-Tenant-Id` automaticamente. Todo texto voltado ao
usuário (rótulos, botões, mensagens de validação/erro) está em português.

## O que seria feito diferente com mais tempo

- **Renovação/expiração de token**: hoje o JWT expira e força um novo login; um refresh token
  silencioso melhoraria a experiência em sessões longas.
- **Testes de contrato automatizados** comparando a resposta real da API ao
  `contracts/openapi.yaml` (hoje a verificação é manual via Swagger UI).
- **Otimização da busca por nome do Beneficiário**: o filtro `pessoaNome` hoje faz um `LIKE`
  case-insensitive via join JPQL; para volumes maiores, um índice funcional
  (`lower(pessoa.nome)`) ou uma coluna denormalizada indexada seria mais eficiente.
- **Auditoria mais rica** (quem criou/alterou cada registro), hoje limitada a
  `created_at`/`updated_at`.
- **CI** rodando `mvn test` (com Testcontainers) e `npm test` a cada push, hoje só documentado
  como comando local.
