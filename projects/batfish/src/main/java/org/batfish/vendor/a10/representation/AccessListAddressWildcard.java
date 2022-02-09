package org.batfish.vendor.a10.representation;

import javax.annotation.Nonnull;
import org.batfish.datamodel.IpWildcard;

/** An access-list address, representing a specific IP wildcard. */
public class AccessListAddressWildcard implements AccessListAddress {
  @Nonnull
  public IpWildcard getWildcard() {
    return _wildcard;
  }

  public AccessListAddressWildcard(IpWildcard wildcard) {
    _wildcard = wildcard;
  }

  @Nonnull private final IpWildcard _wildcard;

  @Override
  public <T> T accept(AccessListAddressVisitor<T> visitor) {
    return visitor.visitWildcard(this);
  }
}
