package com.peergeek.courselearning.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
@ConfigurationProperties("discovery")
public class DiscoverySettings {
    private Duration dirtyFlush = Duration.ofSeconds(5);
    private Duration reconcileInterval = Duration.ofMinutes(5);
    private int maxOffset = 1000;
    private Popular popular = new Popular();
    private Stats stats = new Stats();

    public Duration getDirtyFlush() { return dirtyFlush; }
    public void setDirtyFlush(Duration dirtyFlush) { this.dirtyFlush = dirtyFlush; }
    public Duration getReconcileInterval() { return reconcileInterval; }
    public void setReconcileInterval(Duration reconcileInterval) { this.reconcileInterval = reconcileInterval; }
    public int getMaxOffset() { return maxOffset; }
    public void setMaxOffset(int maxOffset) { this.maxOffset = maxOffset; }
    public Popular getPopular() { return popular; }
    public void setPopular(Popular popular) { this.popular = popular; }
    public Stats getStats() { return stats; }
    public void setStats(Stats stats) { this.stats = stats; }

    public static class Popular {
        private int size = 12;
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
    }

    public static class Stats {
        private int minCompletionSample = 10;
        public int getMinCompletionSample() { return minCompletionSample; }
        public void setMinCompletionSample(int minCompletionSample) { this.minCompletionSample = minCompletionSample; }
    }
}
