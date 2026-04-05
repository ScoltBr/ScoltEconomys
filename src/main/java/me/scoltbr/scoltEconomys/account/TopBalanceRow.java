package me.scoltbr.scoltEconomys.account;

import java.util.UUID;

/** Linha do ranking. {@code name} pode ser null se o jogador nunca salvou o nome. */
public record TopBalanceRow(UUID uuid, String name, double total) {}