package ro.prospero.aidir.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.UserOnboardingPayload;

@Service
public class UserOnboardingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserOnboardingService.class);

    // todo: persist to a user_submission table once there is a liquibase changeset for it, and hash
    //  basic.password on the way in - it must never be stored or logged as received
    public void saveSubmission(UserOnboardingPayload payload) {
        LOGGER.info("Received user onboarding submission for plan {} ({} skills, {} experience entries)",
                    payload.plan().selectedKey().wire(),
                    payload.profile().skills().size(),
                    payload.profile().experience() == null ? 0 : payload.profile().experience().size());
    }
}
