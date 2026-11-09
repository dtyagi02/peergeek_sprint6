CREATE TABLE IF NOT EXISTS referrals (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  inviter_id BIGINT NOT NULL,
  invited_email VARCHAR(254) NOT NULL,
  referral_code VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  referred_user_id BIGINT DEFAULT NULL,
  inviter_reward_amount DECIMAL(19,2) NOT NULL,
  new_user_reward_amount DECIMAL(19,2) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  registered_at TIMESTAMP(6) NULL DEFAULT NULL,
  rewarded_at TIMESTAMP(6) NULL DEFAULT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_referral_code (referral_code),
  UNIQUE KEY uk_referrals_inviter_email_status (inviter_id, invited_email, status),
  UNIQUE KEY uk_referrals_referred_user (referred_user_id),
  KEY idx_referrals_inviter_status (inviter_id, status),
  KEY idx_referrals_invited_email (invited_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

