package com.peergeek.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("discovery")
public class DiscoverySettings {
	private Duration dirtyFlush = Duration.ofSeconds(5);
	private Duration reconcileInterval = Duration.ofMinutes(5);
	private int maxOffset = 1000;
	private Popular popular = new Popular();
	private Stats stats = new Stats();
	private Cache cache = new Cache();

	public Duration getDirtyFlush() { return dirtyFlush; }
	public void setDirtyFlush(Duration value) { this.dirtyFlush = value; }

	public Duration getReconcileInterval() { return reconcileInterval; }
	public void setReconcileInterval(Duration value) { this.reconcileInterval = value; }

	public int getMaxOffset() { return maxOffset; }
	public void setMaxOffset(int value) { this.maxOffset = value; }

	public Popular getPopular() { return popular; }
	public void setPopular(Popular value) { this.popular = value; }

	public Stats getStats() { return stats; }
	public void setStats(Stats value) { this.stats = value; }

	public Cache getCache() { return cache; }
	public void setCache(Cache value) { this.cache = value; }

	public static class Popular {
		private int size = 12;
		public int getSize() { return size; }
		public void setSize(int value) { this.size = value; }
	}

	public static class Stats {
		private int minCompletionSample = 10;
		public int getMinCompletionSample() { return minCompletionSample; }
		public void setMinCompletionSample(int value) { this.minCompletionSample = value; }
	}

	public static class Cache {
		private Duration previewTtl = Duration.ofMinutes(10);
		private Duration popularTtl = Duration.ofMinutes(2);
		private Duration categoriesTtl = Duration.ofMinutes(10);
		private Duration structureTtl = Duration.ofMinutes(10);

		public Duration getPreviewTtl() { return previewTtl; }
		public void setPreviewTtl(Duration value) { this.previewTtl = value; }

		public Duration getPopularTtl() { return popularTtl; }
		public void setPopularTtl(Duration value) { this.popularTtl = value; }

		public Duration getCategoriesTtl() { return categoriesTtl; }
		public void setCategoriesTtl(Duration value) { this.categoriesTtl = value; }

		public Duration getStructureTtl() { return structureTtl; }
		public void setStructureTtl(Duration value) { this.structureTtl = value; }
	}
}
