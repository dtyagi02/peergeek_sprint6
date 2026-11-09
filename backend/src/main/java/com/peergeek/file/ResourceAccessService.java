package com.peergeek.file;

import java.util.List;
import com.peergeek.exception.ForbiddenException;
import com.peergeek.exception.NotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ResourceAccessService {
	private final List<ResourceAccessVoter> voters;

	public ResourceAccessService(List<ResourceAccessVoter> voters) {
		this.voters = List.copyOf(voters);
	}

	public boolean isAllowed(ResourceAccessContext context) {
		boolean granted = false;
		for (ResourceAccessVoter voter : voters) {
			ResourceVote vote = voter.vote(context);
			if (vote == ResourceVote.DENY) {
				return false;
			}
			granted |= vote == ResourceVote.GRANT;
		}
		return granted;
	}

	public void authorize(ResourceAccessContext context, ResourceAccessDeniedMode deniedMode) {
		if (isAllowed(context)) {
			return;
		}
		if (deniedMode == ResourceAccessDeniedMode.NOT_FOUND) {
			throw new NotFoundException("The requested resource was not found");
		}
		throw new ForbiddenException("You do not have permission to access this resource");
	}
}
