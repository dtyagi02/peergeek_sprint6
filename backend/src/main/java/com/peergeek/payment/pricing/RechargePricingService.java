package com.peergeek.payment.pricing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class RechargePricingService {

	private final Map<String, RechargePackage> packages;

	public RechargePricingService() {
		Map<String, RechargePackage> map = new LinkedHashMap<>();
		map.put("pkg_100", RechargePackage.of(
				"pkg_100", "Starter", 100, 100, null, "100 GeekCoins for doubt sessions and learning"
		));
		map.put("pkg_250", RechargePackage.of(
				"pkg_250", "Standard", 250, 250, null, "250 GeekCoins for regular doubt sessions"
		));
		map.put("pkg_500", RechargePackage.of(
				"pkg_500", "Popular", 500, 500, "Popular", "500 GeekCoins — our most popular pack"
		));
		map.put("pkg_1000", RechargePackage.of(
				"pkg_1000", "Pro", 1000, 1000, "Best Value", "1,000 GeekCoins — maximum learning credit"
		));
		this.packages = Collections.unmodifiableMap(map);
	}

	public List<RechargePackage> getAllPackages() {
		return List.copyOf(packages.values());
	}

	public Optional<RechargePackage> getPackage(String packageId) {
		if (packageId == null) return Optional.empty();
		return Optional.ofNullable(packages.get(packageId.trim()));
	}
}

