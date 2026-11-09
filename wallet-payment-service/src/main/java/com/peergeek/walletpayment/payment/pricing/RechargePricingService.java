package com.peergeek.walletpayment.payment.pricing;

import org.springframework.stereotype.Service;

import java.util.*;

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

