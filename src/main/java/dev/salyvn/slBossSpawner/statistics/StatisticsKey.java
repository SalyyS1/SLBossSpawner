package dev.salyvn.slBossSpawner.statistics;

record StatisticsKey(String playerUuid, String bossId, String periodType, String periodKey) {
    String sortKey() {
        return playerUuid + '\u0000' + bossId + '\u0000' + periodType + '\u0000' + periodKey;
    }
}
