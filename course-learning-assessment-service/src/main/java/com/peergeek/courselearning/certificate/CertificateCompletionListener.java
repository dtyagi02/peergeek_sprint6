package com.peergeek.courselearning.certificate;

import com.peergeek.courselearning.enrollment.LessonCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CertificateCompletionListener {

    private static final Logger log = LoggerFactory.getLogger(CertificateCompletionListener.class);

    private final CertificateService certificates;

    public CertificateCompletionListener(CertificateService certificates) {
        this.certificates = certificates;
    }

    @EventListener
    public void onLessonCompleted(LessonCompletedEvent event) {
        log.info("Received LessonCompletedEvent for userId={}, courseId={}, completed={}/{}",
                event.userId(), event.courseId(), event.completedLessons(), event.totalLessons());
        if (event.completedLessons() >= event.totalLessons() && event.totalLessons() > 0) {
            certificates.issueIfEligible(event.userId(), event.courseId());
        }
    }
}

