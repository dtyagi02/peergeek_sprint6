package com.peergeek.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("readiness")
public class ReadinessSettings {
	private Report report = new Report();
	private Runs runs = new Runs();
	private Integrity integrity = new Integrity();
	private Video video = new Video();
	private Pdf pdf = new Pdf();
	private Ooxml ooxml = new Ooxml();
	private Image image = new Image();
	private Links links = new Links();

	public Report getReport() { return report; }
	public void setReport(Report value) { report = value; }
	public Runs getRuns() { return runs; }
	public void setRuns(Runs value) { runs = value; }
	public Integrity getIntegrity() { return integrity; }
	public void setIntegrity(Integrity value) { integrity = value; }
	public Video getVideo() { return video; }
	public void setVideo(Video value) { video = value; }
	public Pdf getPdf() { return pdf; }
	public void setPdf(Pdf value) { pdf = value; }
	public Ooxml getOoxml() { return ooxml; }
	public void setOoxml(Ooxml value) { ooxml = value; }
	public Image getImage() { return image; }
	public void setImage(Image value) { image = value; }
	public Links getLinks() { return links; }
	public void setLinks(Links value) { links = value; }

	public static class Report {
		private Duration maxAge = Duration.ofHours(24);
		private int maxIssues = 500;
		private int courseDescriptionWarningMinLength = 50;
		public Duration getMaxAge() { return maxAge; }
		public void setMaxAge(Duration value) { maxAge = value; }
		public int getMaxIssues() { return maxIssues; }
		public void setMaxIssues(int value) { maxIssues = value; }
		public int getCourseDescriptionWarningMinLength() { return courseDescriptionWarningMinLength; }
		public void setCourseDescriptionWarningMinLength(int value) { courseDescriptionWarningMinLength = value; }
	}

	public static class Runs {
		private Duration timeout = Duration.ofMinutes(10);
		private Duration courseCooldown = Duration.ofSeconds(10);
		private int maxConcurrentPerUser = 3;
		private int threads = 4;
		private int queueCapacity = 32;
		private int retentionPerCourse = 10;
		public Duration getTimeout() { return timeout; }
		public void setTimeout(Duration value) { timeout = value; }
		public Duration getCourseCooldown() { return courseCooldown; }
		public void setCourseCooldown(Duration value) { courseCooldown = value; }
		public int getMaxConcurrentPerUser() { return maxConcurrentPerUser; }
		public void setMaxConcurrentPerUser(int value) { maxConcurrentPerUser = value; }
		public int getThreads() { return threads; }
		public void setThreads(int value) { threads = value; }
		public int getQueueCapacity() { return queueCapacity; }
		public void setQueueCapacity(int value) { queueCapacity = value; }
		public int getRetentionPerCourse() { return retentionPerCourse; }
		public void setRetentionPerCourse(int value) { retentionPerCourse = value; }
	}

	public static class Integrity {
		private long maxRehashBytes = 50L * 1024 * 1024;
		public long getMaxRehashBytes() { return maxRehashBytes; }
		public void setMaxRehashBytes(long value) { maxRehashBytes = value; }
	}

	public static class Video {
		private boolean probeRequired = true;
		private Duration timeout = Duration.ofSeconds(30);
		public boolean isProbeRequired() { return probeRequired; }
		public void setProbeRequired(boolean value) { probeRequired = value; }
		public Duration getTimeout() { return timeout; }
		public void setTimeout(Duration value) { timeout = value; }
	}

	public static class Pdf {
		private long maxBytes = 50L * 1024 * 1024;
		public long getMaxBytes() { return maxBytes; }
		public void setMaxBytes(long value) { maxBytes = value; }
	}

	public static class Ooxml {
		private int maxEntries = 10000;
		private long maxExpandedBytes = 64L * 1024 * 1024;
		private double maxCompressionRatio = 100.0;
		public int getMaxEntries() { return maxEntries; }
		public void setMaxEntries(int value) { maxEntries = value; }
		public long getMaxExpandedBytes() { return maxExpandedBytes; }
		public void setMaxExpandedBytes(long value) { maxExpandedBytes = value; }
		public double getMaxCompressionRatio() { return maxCompressionRatio; }
		public void setMaxCompressionRatio(double value) { maxCompressionRatio = value; }
	}

	public static class Image {
		private long maxPixels = 50_000_000L;
		public long getMaxPixels() { return maxPixels; }
		public void setMaxPixels(long value) { maxPixels = value; }
	}

	public static class Links {
		private Duration cacheTtl = Duration.ofMinutes(15);
		private boolean allowPrivateHosts;
		private List<Integer> allowedPorts = List.of(80, 443);
		private Duration connectTimeout = Duration.ofSeconds(3);
		private Duration totalTimeout = Duration.ofSeconds(8);
		private int maxRedirects = 3;
		private int maxConcurrent = 8;
		private int maxConcurrentPerHost = 2;
		public Duration getCacheTtl() { return cacheTtl; }
		public void setCacheTtl(Duration value) { cacheTtl = value; }
		public boolean isAllowPrivateHosts() { return allowPrivateHosts; }
		public void setAllowPrivateHosts(boolean value) { allowPrivateHosts = value; }
		public List<Integer> getAllowedPorts() { return allowedPorts; }
		public void setAllowedPorts(List<Integer> value) { allowedPorts = List.copyOf(value); }
		public Duration getConnectTimeout() { return connectTimeout; }
		public void setConnectTimeout(Duration value) { connectTimeout = value; }
		public Duration getTotalTimeout() { return totalTimeout; }
		public void setTotalTimeout(Duration value) { totalTimeout = value; }
		public int getMaxRedirects() { return maxRedirects; }
		public void setMaxRedirects(int value) { maxRedirects = value; }
		public int getMaxConcurrent() { return maxConcurrent; }
		public void setMaxConcurrent(int value) { maxConcurrent = value; }
		public int getMaxConcurrentPerHost() { return maxConcurrentPerHost; }
		public void setMaxConcurrentPerHost(int value) { maxConcurrentPerHost = value; }
	}
}
