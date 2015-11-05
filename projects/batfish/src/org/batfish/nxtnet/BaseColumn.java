package org.batfish.nxtnet;

import java.util.Arrays;
import java.util.List;

import org.batfish.common.BatfishException;
import org.batfish.representation.BgpAdvertisement;
import org.batfish.representation.Flow;
import org.batfish.representation.Ip;
import org.batfish.representation.PrecomputedRoute;
import org.batfish.representation.Prefix;

import com.google.common.collect.ImmutableList;

public abstract class BaseColumn<T> implements Column {

   protected List<T> _items;

   private ColumnType _type;

   public BaseColumn(T[] items, ColumnType type) {
      _items = ImmutableList.<T> builder().addAll(Arrays.asList(items)).build();
      _type = type;
   }

   @Override
   public List<BgpAdvertisement> asBgpAdvertisementList(EntityTable entityTable) {
      throw new BatfishException("unsupported column view");
   }

   @Override
   public List<Flow> asFlowList(EntityTable entityTable) {
      throw new BatfishException("unsupported column view");
   }

   @Override
   public List<Ip> asIpList() {
      throw new BatfishException("unsupported column view");
   }

   @Override
   public List<Prefix> asPrefixList(EntityTable entityTable) {
      throw new BatfishException("unsupported column view");
   }

   @Override
   public List<PrecomputedRoute> asRouteList(EntityTable entityTable) {
      throw new BatfishException("unsupported column view");
   }

   @Override
   public List<String> asStringList() {
      throw new BatfishException("unsupported column view");
   }

   public List<T> getRows() {

      return _items;
   }

   @Override
   public final ColumnType getType() {
      return _type;
   }

}
