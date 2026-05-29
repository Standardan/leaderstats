package io.github.standardan.leaderstats.storage;

/** One row of a leaderboard: a player's name and their value for some stat. */
public record LeaderEntry(String name, long value) {
}
