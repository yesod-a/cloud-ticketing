package com.cloudticket.inventory;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.inventory.reconcile.InventoryCachePreheatJob;
import com.cloudticket.inventory.reconcile.InventoryCachePreheatService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InventoryCachePreheatJobTest {
  @Test
  void xxlJobDelegatesToPreheatService() {
    InventoryCachePreheatService service = Mockito.mock(InventoryCachePreheatService.class);
    when(service.preheatDue("42", 100)).thenReturn(2);
    InventoryCachePreheatJob job = new InventoryCachePreheatJob(service, 100);

    job.execute("42");

    verify(service).preheatDue("42", 100);
  }
}
