package com.peergeek.directmessage;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface DirectConversationRepository extends JpaRepository<DirectConversationEntity,Long> {
	@Query("select c from DirectConversationEntity c where c.userOne.id=:a and c.userTwo.id=:b or c.userOne.id=:b and c.userTwo.id=:a")
	Optional<DirectConversationEntity> findBetween(@Param("a") Long a,@Param("b") Long b);
	@Query("select c from DirectConversationEntity c where c.userOne.id=:id or c.userTwo.id=:id order by c.updatedAt desc")
	List<DirectConversationEntity> findForUser(@Param("id") Long id);
}
