<p align="center">
  <img src="./docs/assets/logo.png" alt="Lola Cloud Logo" width="150" />
</p>

<h1 align="center">Lola Cloud • Runway</h1>

<p align="center">
  <strong>Orquestrador de Borda, Zonas DNS, Túneis Zero Trust e DDNS (Equivalente ao AWS Route 53 & Cloudflare Edge)</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 25" />
  <img src="https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 4.1.1" />
  <img src="https://img.shields.io/badge/Cloudflare_API-v4-F38020?style=flat-square&logo=cloudflare&logoColor=white" alt="Cloudflare Edge" />
  <img src="https://img.shields.io/badge/PostgreSQL-17-4169E1?style=flat-square&logo=postgresql&logoColor=white" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Port-8083-blue?style=flat-square" alt="Port 8083" />
</p>

---

## 📌 Visão Geral

O **Lola Runway** é o componente de infraestrutura de rede e borda (*Edge Networking*) da **Lola Cloud**. Ele integra-se diretamente com a **Cloudflare Edge API v4** para automação de zonas de domínio DNS, publicação de túneis Zero Trust (`cloudflared`) e manutenção contínua de apontamentos dinâmicos via DDNS (**Auto-IP Fix**).

---

## 🌐 Principais Responsabilidades

1. **Gestão de Contas Cloudflare**: Armazenamento seguro de credenciais (API Tokens / Global API Keys) e sincronização periódica de zonas DNS.
2. **Túneis Zero Trust (`cloudflared`)**: Monitoramento ativo de conectores de túneis, uptime, regras de roteamento e status de saúde (`HEALTHY`, `DOWN`, `INACTIVE`) consultados em tempo real na Cloudflare Edge API.
3. **DNS & DDNS (Auto-IP Fix)**:
   - Detecção contínua do endereço IP público WAN do host.
   - Sincronização automática de registros DNS do tipo **A** (com proxy desativado para bancos relacionais e serviços TCP/TLS).
   - Integração com o **Lola Relate** para criação dinâmica de subdomínios para novas instâncias de banco de dados.

---

## 📡 Principais Endpoints da API REST

| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `GET` | `/api/v1/runway/accounts` | Lista contas Cloudflare configuradas |
| `POST` | `/api/v1/runway/accounts` | Cadastra nova conta com validação de credenciais |
| `GET` | `/api/v1/runway/zones` | Lista zonas DNS sincronizadas |
| `GET` | `/api/v1/runway/zones/{zoneId}/records` | Lista registros DNS de uma zona |
| `POST` | `/api/v1/runway/records` | Cria registro DNS com suporte a flag DDNS Auto-IP |
| `DELETE` | `/api/v1/runway/records/{id}` | Remove registro DNS |
| `GET` | `/api/v1/runway/tunnels` | Lista túneis Cloudflare cadastrados |
| `GET` | `/api/v1/runway/tunnels/{id}` | Detalhes e status ao vivo via Edge API |
| `GET` | `/api/v1/runway/edge-ip` | Retorna o IP público WAN detectado do servidor |

---

## 🛠️ Stack Tecnológica

- **Linguagem**: Java 25 (OpenJDK)
- **Framework**: Spring Boot 4.1.1
- **Integração Externa**: Cloudflare Edge API v4 via Spring `RestClient` e Java `HttpClient`
- **Segurança**: Filtro de autenticação JWT via introspecção com LCI (`LciAuthFilter`)
- **Persistência**: Spring Data JPA / Flyway
- **Banco de Dados**: PostgreSQL 17 (`jdbc:postgresql://localhost:5433/lola_runway`)

---

## 🚀 Como Executar

```bash
# Executar a aplicação
./mvnw spring-boot:run
```
Porta padrão: **`8083`**
