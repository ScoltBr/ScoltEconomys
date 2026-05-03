# 💰 ScoltEconomys

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)
![Paper](https://img.shields.io/badge/Paper-1.21.1+-blue?style=for-the-badge)
![Version](https://img.shields.io/badge/Version-1.1.0-green?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)

O **ScoltEconomys** é um ecossistema financeiro completo e de alta precisão para servidores Minecraft. Diferente de plugins de economia comuns, ele utiliza o motor matemático `BigDecimal` para garantir que nenhuma fração de centavo seja perdida em cálculos complexos, taxas ou juros.

---

## 🚀 Diferenciais Exclusivos

### 🪙 Precisão Financeira Absoluta (BigDecimal)
Esqueça erros de arredondamento comuns em double/float. Todo o sistema financeiro, do `/money` ao Mercado de Ações, opera com precisão arbitrária, ideal para economias com números gigantescos ou transações bancárias minuciosas.

### 🌍 Mercado Global de Commodities
Um sistema dinâmico de oferta e demanda. Os jogadores podem vender recursos (minérios, energia, agrícolas) para um mercado global onde os preços flutuam em tempo real com base no volume de vendas e eventos econômicos.
- **Setores:** Metais, Energia, Agrícola, Raros.
- **Variação Dinâmica:** Preços sobem e descem conforme a escassez.

### 📈 Bolsa de Valores (Stock Market)
Invista em empresas virtuais do servidor.
- **Drift & Pressão:** Algoritmo sofisticado de oscilação.
- **Dividendos:** Receba lucros passivos por suas ações.
- **Portfólio:** Acompanhe seus investimentos em uma interface moderna.

### 🏦 Sistema Bancário & Tesouro
- **Banco:** Proteja seu dinheiro, ganhe juros e pague impostos de renda automáticos.
- **Tesouro:** O servidor possui um tesouro central que coleta taxas, podendo ser usado para eventos ou gestão governamental.

---

## 🛠️ Comandos e Funcionalidades

| Comando | Descrição | Aliases |
| :--- | :--- | :--- |
| `/money` | Comando principal de economia. | `/bal`, `/carteira` |
| `/commodities` | Acessa o Mercado Global de Commodities. | `/comm`, `/mercado` |
| `/bolsa` | Interface da Bolsa de Valores. | `/stocks`, `/investir` |
| `/pay <player> <val>` | Transfere dinheiro para outro jogador. | `/enviar` |
| `/eco <admin>` | Gerenciamento administrativo completo. | `/economy` |

---

## 🎨 Visual Moderno (MiniMessage)
O plugin utiliza a tecnologia **MiniMessage**, permitindo cores vibrantes, gradientes suaves e elementos interativos no chat (hover e click) que facilitam a navegação do jogador sem poluir a tela.

- **Dica:** O sistema suporta tanto as tags modernas `<color>` quanto os códigos legados `&a`, convertendo-os automaticamente para o formato de alta performance.

---

## 🏗️ Arquitetura Técnica
O projeto foi construído seguindo os melhores padrões de desenvolvimento:
- **Async Processing:** Operações de banco de dados e cálculos de mercado não travam a main thread.
- **HikariCP:** Pool de conexões otimizado para MySQL/MariaDB.
- **Caffeine Cache:** Acesso instantâneo a saldos de jogadores online.
- **Modularidade:** Fácil expansão para novos tipos de mercados (ex: Forex, Cripto).

---

## 🔧 Instalação e Requisitos
1. **Versão:** Minecraft 1.21.1 ou superior.
2. **Java:** 21.
3. **Dependências:** Requer um provedor de banco de dados (MySQL recomendado).
4. **Build:** Utilize Maven para compilar o `.jar`.

```bash
mvn clean package
```

---

## 👨‍💻 Créditos e Contato
**Desenvolvido por ScoltBr**
*Focado em criar sistemas econômicos que transformam a gameplay em uma simulação viva e competitiva.*

---
> [!TIP]
> Use `/commodities listar` para ver as melhores oportunidades de lucro hoje!
