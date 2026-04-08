# 💰 ScoltEconomy

Sistema de economia avançado para servidores **Paper 1.21.1+**,
projetado para alta performance, escalabilidade e controle
administrativo completo.

------------------------------------------------------------------------

## 🚀 Visão Geral

O **ScoltEconomy** é um plugin de economia moderno com arquitetura
modular, foco em performance e preparado para servidores com **300+
jogadores simultâneos**.

Desenvolvido com princípios de:

-   🧠 Arquitetura limpa
-   ⚡ Operações assíncronas
-   📦 Cache inteligente
-   🛢️ Persistência em MySQL
-   📊 Monitoramento econômico
-   🔌 Compatibilidade completa com Vault
-   📡 Sistema de API Pública para desenvolvedores

------------------------------------------------------------------------

## 🏗️ Arquitetura

O plugin é dividido em módulos independentes:

-   `account/` → Sistema de contas (wallet + banco)
-   `bank/` → Sistema bancário com juros
-   `tax/` → Sistema de impostos dinâmicos
-   `stats/` → Monitoramento e saúde econômica
-   `admin/` → GUI administrativa
-   `stock/` → Mercado de Ações (Bolsa de Valores)
-   `api/` → Interface pública e eventos customizados
-   `database/` → Conexão MySQL + HikariCP
-   `scheduler/` → Execução assíncrona segura
-   `vault/` → Integração completa com Vault API

------------------------------------------------------------------------

## 🔥 Funcionalidades

### 💳 Sistema de Contas

-   Wallet e Banco separados
-   Cache em memória com Caffeine
-   Salvamento em batch assíncrono
-   Lock por UUID (thread-safe)

### 📈 Mercado de Ações (Beta)

-   Simulação dinâmica baseada em "Drift" e "Pressão"
-   Setores econômicos com bônus/penalidades
-   GUI de investimentos intuitiva (`/bolsa`)
-   Histórico de preços persistente

### 🏦 Sistema Bancário

-   Depósito e saque
-   Juros configuráveis e automáticos
-   Taxa de saque progressiva
-   Controle anti-inflação

### 📊 Economia Inteligente

-   Cálculo de concentração de riqueza
-   Alertas automáticos de inflação no Admin Panel
-   Snapshot diário da saúde do servidor

### 🔌 API Pública e Eventos

-   Eventos canceláveis para integração (ex: BalanceChange)
-   Acesso simplificado via `ScoltAPI.get()`
-   Documentação Javadoc em todos os métodos

------------------------------------------------------------------------

## 🛠️ Para Desenvolvedores

### Dependência (Maven)

```xml
<dependency>
    <groupId>me.scoltbr</groupId>
    <artifactId>scolteconomy</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

### Usando a API

```java
// Obter a instância da API
ScoltEconomyAPI api = ScoltAPI.get();

// Consultar saldo de forma assíncrona
api.getWalletAsync(uuid, balance -> {
    player.sendMessage("Saldo: " + balance);
});

// Transferência com taxas
api.transfer(from, to, 1000.0, result -> {
    if (result.success()) {
        System.out.println("Enviado! Taxa retida: " + result.fee());
    }
});
```

### Eventos Disponíveis

-   `AccountBalanceChangeEvent`: Disparado em qualquer alteração de saldo. Pode ser cancelado.
-   `StockPriceUpdateEvent`: Disparado quando o preço de uma ação oscila.
-   `StockTransactionEvent`: Disparado após uma compra ou venda de ações.

------------------------------------------------------------------------

## ⚙️ Tecnologias Utilizadas

-   Java 21
-   Paper API 1.21.1+
-   Maven
-   MySQL + HikariCP
-   Caffeine Cache
-   Vault API

------------------------------------------------------------------------

## 👨‍💻 Desenvolvido por

**ScoltBr**\
Projeto focado em arquitetura de plugins premium e economia escalável
para Minecraft.
