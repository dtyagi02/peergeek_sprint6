package com.peergeek.discovery;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import com.peergeek.common.SafeCache;
import com.peergeek.config.DiscoverySettings;
import org.springframework.stereotype.Service;

@Service
public class DiscoveryCacheService {
	private final SafeCache safeCache;
	private final DiscoverySettings settings;
	private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

	public DiscoveryCacheService(SafeCache safeCache, DiscoverySettings settings) {
		this.safeCache = safeCache;
		this.settings = settings;
	}

	public CoursePreviewDto getOrLoadPreview(long courseId, Supplier<CoursePreviewDto> loader) {
		String key = "course:preview:" + courseId;
		Duration ttl = settings.getCache().getPreviewTtl();
		return singleFlight(key, () -> {
			DiscoveryCacheEnvelopes.CoursePreviewEnvelope envelope = safeCache.getOrLoad(
					key,
					DiscoveryCacheEnvelopes.CoursePreviewEnvelope.class,
					ttl,
					() -> {
						CoursePreviewDto payload = loader.get();
						return payload == null
								? null
								: new DiscoveryCacheEnvelopes.CoursePreviewEnvelope(
										DiscoveryCacheEnvelopes.CURRENT_SCHEMA_VERSION, payload);
					});

			if (envelope == null) {
				return null;
			}
			if (envelope.schemaVersion() != DiscoveryCacheEnvelopes.CURRENT_SCHEMA_VERSION) {
				CoursePreviewDto fresh = loader.get();
				return fresh;
			}
			return envelope.payload();
		});
	}

	public List<CoursePreviewDto.ModulePreviewDto> getOrLoadStructure(
			long courseId, Supplier<List<CoursePreviewDto.ModulePreviewDto>> loader) {
		String key = "course:structure:" + courseId;
		Duration ttl = settings.getCache().getStructureTtl();
		return singleFlight(key, () -> {
			DiscoveryCacheEnvelopes.PublicOutlineEnvelope envelope = safeCache.getOrLoad(
					key,
					DiscoveryCacheEnvelopes.PublicOutlineEnvelope.class,
					ttl,
					() -> {
						List<CoursePreviewDto.ModulePreviewDto> payload = loader.get();
						return payload == null
								? null
								: new DiscoveryCacheEnvelopes.PublicOutlineEnvelope(
										DiscoveryCacheEnvelopes.CURRENT_SCHEMA_VERSION, payload);
					});

			if (envelope == null) {
				return List.of();
			}
			if (envelope.schemaVersion() != DiscoveryCacheEnvelopes.CURRENT_SCHEMA_VERSION) {
				return loader.get();
			}
			return envelope.payload();
		});
	}

	public List<CourseCardDto> getOrLoadPopular(Supplier<List<CourseCardDto>> loader) {
		String key = "courses:popular";
		Duration ttl = settings.getCache().getPopularTtl();
		return singleFlight(key, () -> {
			DiscoveryCacheEnvelopes.PopularCoursesEnvelope envelope = safeCache.getOrLoad(
					key,
					DiscoveryCacheEnvelopes.PopularCoursesEnvelope.class,
					ttl,
					() -> {
						List<CourseCardDto> payload = loader.get();
						return payload == null
								? null
								: new DiscoveryCacheEnvelopes.PopularCoursesEnvelope(
										DiscoveryCacheEnvelopes.CURRENT_SCHEMA_VERSION, payload);
					});

			if (envelope == null) {
				return List.of();
			}
			if (envelope.schemaVersion() != DiscoveryCacheEnvelopes.CURRENT_SCHEMA_VERSION) {
				return loader.get();
			}
			return envelope.payload();
		});
	}

	public List<PublicCategoryDto> getOrLoadCategories(Supplier<List<PublicCategoryDto>> loader) {
		String key = "categories:all";
		Duration ttl = settings.getCache().getCategoriesTtl();
		return singleFlight(key, () -> {
			DiscoveryCacheEnvelopes.PublicCategoriesEnvelope envelope = safeCache.getOrLoad(
					key,
					DiscoveryCacheEnvelopes.PublicCategoriesEnvelope.class,
					ttl,
					() -> {
						List<PublicCategoryDto> payload = loader.get();
						return payload == null
								? null
								: new DiscoveryCacheEnvelopes.PublicCategoriesEnvelope(
										DiscoveryCacheEnvelopes.CURRENT_SCHEMA_VERSION, payload);
					});

			if (envelope == null) {
				return List.of();
			}
			if (envelope.schemaVersion() != DiscoveryCacheEnvelopes.CURRENT_SCHEMA_VERSION) {
				return loader.get();
			}
			return envelope.payload();
		});
	}

	private <T> T singleFlight(String key, Supplier<T> supplier) {
		Object lock = keyLocks.computeIfAbsent(key, k -> new Object());
		synchronized (lock) {
			try {
				return supplier.get();
			} finally {
				keyLocks.remove(key, lock);
			}
		}
	}
}
