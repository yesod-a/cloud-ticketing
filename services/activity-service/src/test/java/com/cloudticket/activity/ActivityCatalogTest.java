package com.cloudticket.activity;
import static org.junit.jupiter.api.Assertions.*;
import com.cloudticket.activity.service.ActivityCatalog;
import org.junit.jupiter.api.Test;
class ActivityCatalogTest { @Test void publishesIndependentActivitiesAndFreezesLayoutVersion() { var catalog=new ActivityCatalog(); assertEquals(2,catalog.publicActivities().size()); var draft=catalog.create("新活动","主办方"); assertThrows(IllegalStateException.class,()->catalog.freezeLayout(draft.id())); catalog.publish(draft.id()); catalog.freezeLayout(draft.id()); assertThrows(IllegalStateException.class,()->catalog.freezeLayout(draft.id())); } }
