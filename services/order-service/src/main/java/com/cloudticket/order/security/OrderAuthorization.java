package com.cloudticket.order.security;
public final class OrderAuthorization {
  public boolean canRead(String subject,String owner,String permissions){return subject!=null&&subject.equals(owner)||permissions.contains("order:read");}
  public boolean canWrite(String subject,String owner,String permissions){return subject!=null&&subject.equals(owner)||permissions.contains("order:cancel");}
  public boolean canRead(String subject,String owner,String permissions,String scopes,String resourceType,String resourceId){return subject!=null&&subject.equals(owner)||ScopeMatcher.allows(permissions,scopes,resourceType,resourceId);}
  public boolean canWrite(String subject,String owner,String permissions,String scopes,String resourceType,String resourceId){return subject!=null&&subject.equals(owner)||ScopeMatcher.allows(permissions,scopes,resourceType,resourceId);}
  public boolean canRequestRefund(String subject,String owner){return subject!=null&&subject.equals(owner);}
  public boolean canReviewRefund(String permissions){return permissions!=null&&(permissions.contains("order:refund")||permissions.contains("system:config"));}
  public boolean canReviewRefund(String permissions,String scopes,String sessionId){
    return canReviewRefund(permissions) && ScopeMatcher.allows(permissions,scopes,"SESSION",sessionId);
  }
}
