# 💰 ScoltEconomy

Sistema de economia avançado para servidores **Paper 1.21.11+**,
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

------------------------------------------------------------------------

## 🏗️ Arquitetura

O plugin é dividido em módulos independentes:

-   `account/` → Sistema de contas (wallet + banco)
-   `bank/` → Sistema bancário com juros
-   `tax/` → Sistema de impostos dinâmicos
-   `stats/` → Monitoramento e saúde econômica
-   `admin/` → GUI administrativa
-   `database/` → Conexão MySQL + HikariCP
-   `scheduler/` → Execução assíncrona segura
-   `vault/` → Integração completa com Vault API

------------------------------------------------------------------------

## 🔥 Funcionalidades

### 💳 Sistema de Contas

-   Wallet e Banco separados
-   Cache em memória
-   Salvamento em batch assíncrono
-   Lock por UUID (thread-safe)

### 💸 Sistema de Pagamentos

-   `/money pay`
-   Suporte a valores como:
    -   1K
    -   2.5M
    -   1B
    -   3T
    -   1Q

### 🏦 Sistema Bancário

-   Depósito e saque
-   Juros configuráveis
-   Taxa de saque
-   Controle anti-inflação

### 📊 Economia Inteligente

-   Cálculo de concentração (Top 10%)
-   Crescimento 24h
-   Snapshot diário
-   Alertas automáticos de inflação

### 🏆 Money Top

-   Rank 1--10
-   Baseado em wallet + banco
-   Consulta otimizada no MySQL
-   Formatação compacta automática (1B, 2T, etc)

### 🧾 Sistema de Impostos

-   Transferência
-   Saque
-   Ajustável via GUI em tempo real
-   Persistência automática em config

### 🖥️ GUI Administrativa

-   Painel econômico completo
-   Estatísticas globais
-   Alertas
-   Ajuste de impostos
-   Atualização dinâmica

### 🔌 Compatibilidade Vault

-   Provider Economy completo
-   Suporte a OfflinePlayer
-   Integração com plugins de loja, ranks, etc

------------------------------------------------------------------------

## ⚙️ Tecnologias Utilizadas

-   Java 21
-   Paper API 1.21.11
-   Maven
-   MySQL
-   HikariCP (connection pooling)
-   Vault API
-   Arquitetura orientada a serviços
-   Execução assíncrona controlada
-   Design modular escalável

------------------------------------------------------------------------

## 📈 Performance

-   Nenhuma query MySQL na main thread
-   Batch saving configurável
-   Cache em memória para operações críticas
-   Suporte real para 300+ jogadores

------------------------------------------------------------------------

## 🧠 Diferenciais Técnicos

-   Separação clara de responsabilidades
-   Thread-safety com AtomicBoolean e locks por UUID
-   Snapshot econômico persistente
-   Sistema preparado para expansão (empresas, mercado dinâmico,
    redistribuição automática)

------------------------------------------------------------------------

## 📌 Roadmap

-   Sistema de Empresas
-   Mercado Dinâmico (oferta e demanda)
-   Sistema Anti-Lavagem
-   Redistribuição progressiva
-   Gráficos econômicos avançados

------------------------------------------------------------------------

## 👨‍💻 Desenvolvido por

**ScoltBr**\
Projeto focado em arquitetura de plugins premium e economia escalável
para Minecraft.
