package com.peergeek.course;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
	private final CategoryRepository categories;

	public CategoryService(CategoryRepository categories) {
		this.categories = categories;
	}

	@Transactional(readOnly = true)
	public List<CategoryDto> getActiveCategories() {
		return categories.findAllByActiveTrueOrderBySortOrderAscNameAsc().stream()
				.map(CategoryDto::from).toList();
	}
}
