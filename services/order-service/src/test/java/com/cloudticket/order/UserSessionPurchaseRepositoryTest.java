package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.order.persistence.UserSessionPurchaseRepository;
import com.cloudticket.order.persistence.mapper.UserSessionPurchaseMapper;
import org.junit.jupiter.api.Test;

class UserSessionPurchaseRepositoryTest {
  private final UserSessionPurchaseMapper mapper = mock(UserSessionPurchaseMapper.class);
  private final UserSessionPurchaseRepository repository = new UserSessionPurchaseRepository(mapper);

  @Test
  void reservesQuotaAgainstTheUserSessionAggregate() {
    when(mapper.ensure("u1", "s1")).thenReturn(1);

    when(mapper.reserve("u1", "s1", 2, 4, 900)).thenReturn(1);

    repository.reserve("u1", "s1", 2, 4, 900);

    verify(mapper).ensure("u1", "s1");
    verify(mapper).reserve("u1", "s1", 2, 4, 900);
  }

  @Test
  void releasesOnlyTheRequestedQuantityForTheUserSession() {
    when(mapper.release("u1", "s1", 2)).thenReturn(1);

    repository.release("u1", "s1", 2);

    verify(mapper).release("u1", "s1", 2);
  }

  @Test
  void rejectsWhenAggregateCannotReserveTheLimit() {
    when(mapper.ensure("u1", "s1")).thenReturn(1);
    when(mapper.reserve("u1", "s1", 2, 2, 900)).thenReturn(0);

    assertThrows(UserSessionPurchaseRepository.PurchaseLimitExceededException.class,
        () -> repository.reserve("u1", "s1", 2, 2, 900));
    verify(mapper).reserve("u1", "s1", 2, 2, 900);
  }
}
