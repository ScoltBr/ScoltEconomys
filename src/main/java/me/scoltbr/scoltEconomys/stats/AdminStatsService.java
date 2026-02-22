// src/main/java/me/scoltbr/scoltEconomys/stats/AdminStatsService.java
package me.scoltbr.scoltEconomys.stats;

import java.time.LocalDate;
import java.util.Optional;

public final class AdminStatsService {

    private final EconomyCalculator calculator;
    private final EconomyDailyRepository repo;

    public AdminStatsService(EconomyCalculator calculator, EconomyDailyRepository repo) {
        this.calculator = calculator;
        this.repo = repo;
    }

    /**
     * Calcula o snapshot atual (em memória).
     * Não salva nada no banco aqui.
     */
    public EconomySnapshot calculateNow() {
        return calculator.calculate();
    }

    /**
     * Persiste (upsert) o snapshot do dia atual.
     * Chamado pelo StatsTickService periodicamente.
     */
    public void persistToday(EconomySnapshot snapshot) {
        repo.upsert(LocalDate.now(), snapshot);
    }

    /**
     * Crescimento 24h = (totalHoje - totalOntem) / totalOntem
     * Retorna Optional.empty() se não houver dados suficientes.
     */
    public Optional<Double> growth24h() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        Optional<EconomyDailyRow> t = repo.load(today);
        Optional<EconomyDailyRow> y = repo.load(yesterday);

        if (t.isEmpty() || y.isEmpty()) return Optional.empty();

        double todayTotal = t.get().totalCoins();
        double yTotal = y.get().totalCoins();

        if (yTotal <= 0.0) return Optional.empty();

        return Optional.of((todayTotal - yTotal) / yTotal);
    }

    /**
     * Expor os registros diários (opcional, útil pra gráficos depois).
     */
    public Optional<EconomyDailyRow> loadDay(LocalDate day) {
        return repo.load(day);
    }
}
