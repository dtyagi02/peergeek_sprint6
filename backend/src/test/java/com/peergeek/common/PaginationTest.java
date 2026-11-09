package com.peergeek.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.peergeek.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class PaginationTest {

	@Test
	void defaultsAndCapsPageSize() {
		assertThat(Pagination.pageable(null, null, null).getPageNumber()).isEqualTo(0);
		assertThat(Pagination.pageable(null, null, null).getPageSize()).isEqualTo(20);
		assertThat(Pagination.pageable(0, 500, "id,desc").getPageSize()).isEqualTo(100);
		assertThat(Pagination.pageable(0, 50, "id,desc").getSort().getOrderFor("id").isDescending()).isTrue();
	}

	@Test
	void rejectsInvalidPageAndSortSyntax() {
		assertThatThrownBy(() -> Pagination.pageable(-1, 20, null))
				.isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> Pagination.pageable(0, 20, "createdAt"))
				.isInstanceOf(BadRequestException.class);
	}
}
