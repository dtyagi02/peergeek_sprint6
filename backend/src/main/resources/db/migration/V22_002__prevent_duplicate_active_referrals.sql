ALTER TABLE referrals
    ADD CONSTRAINT uk_referrals_inviter_email_status
        UNIQUE (inviter_id, invited_email, status);
