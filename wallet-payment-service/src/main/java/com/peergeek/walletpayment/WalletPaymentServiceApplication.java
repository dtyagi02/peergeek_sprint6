package com.peergeek.walletpayment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WalletPaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletPaymentServiceApplication.class, args);
    }
}

