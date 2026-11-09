package com.peergeek.file;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ResourceProcessingDispatcher {
	private final TaskExecutor executor;
	private final ResourceProcessingWorker worker;

	public ResourceProcessingDispatcher(
			@Qualifier("resourceProcessingExecutor") TaskExecutor executor,
			ResourceProcessingWorker worker) {
		this.executor = executor;
		this.worker = worker;
	}

	public void dispatchAfterCommit(long resourceId) {
		Runnable dispatch = () -> executor.execute(() -> worker.process(resourceId));
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					dispatch.run();
				}
			});
		} else {
			dispatch.run();
		}
	}
}
