package org.batfish.common.bdd;

import static org.batfish.common.bdd.BDDOps.orNull;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.datamodel.IpProtocol;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.SubRange;
import org.batfish.datamodel.TcpFlagsMatchConditions;

/** Convert a {@link HeaderSpace headerspace constraint} to a {@link BDD}. */
public final class HeaderSpaceToBDD {
  private final BDDFactory _bddFactory;
  private final BDDPacket _bddPacket;
  private final IpSpaceToBDD _dstIpSpaceToBdd;
  private final IpSpaceToBDD _srcIpSpaceToBdd;

  public HeaderSpaceToBDD(BDDPacket bddPacket, Map<String, IpSpace> namedIpSpaces) {
    _bddFactory = bddPacket.getFactory();
    _bddPacket = bddPacket;
    _dstIpSpaceToBdd =
        namedIpSpaces.isEmpty()
            ? bddPacket.getDstIpSpaceToBDD()
            : new IpSpaceToBDD(bddPacket.getDstIpSpaceToBDD(), namedIpSpaces);
    _srcIpSpaceToBdd =
        namedIpSpaces.isEmpty()
            ? bddPacket.getSrcIpSpaceToBDD()
            : new IpSpaceToBDD(bddPacket.getSrcIpSpaceToBDD(), namedIpSpaces);
  }

  /** Returns bdd.not() or {@code null} if given {@link BDD} is null. */
  private static @Nullable BDD negateIfNonNull(BDD bdd) {
    return bdd == null ? bdd : bdd.notEq();
  }

  /**
   * A variant of {@link BDDOps#or(BDD...)} that returns {@code null} when all disjuncts are null.
   */
  @VisibleForTesting
  static BDD orWithNull(BDD bdd1, BDD bdd2) {
    if (bdd1 == null && bdd2 == null) {
      return null;
    }
    if (bdd1 == null) {
      return bdd2;
    }
    if (bdd2 == null) {
      return bdd1;
    }
    return bdd1.orWith(bdd2);
  }

  public IpSpaceToBDD getDstIpSpaceToBdd() {
    return _dstIpSpaceToBdd;
  }

  public IpSpaceToBDD getSrcIpSpaceToBdd() {
    return _srcIpSpaceToBdd;
  }

  @Nullable
  private BDD toBDD(Collection<Integer> ints, BDDInteger var) {
    return orNull(ints, i -> var.value((long) i));
  }

  @VisibleForTesting
  @Nullable
  static BDD toBDD(@Nullable IpSpace ipSpace, IpSpaceToBDD toBdd) {
    return ipSpace == null ? null : toBdd.visit(ipSpace);
  }

  @Nullable
  private BDD toBDD(Set<IpProtocol> ipProtocols) {
    return orNull(ipProtocols, _bddPacket.getIpProtocol()::value);
  }

  @Nullable
  private BDD toBDD(@Nullable Set<SubRange> ranges, BDDInteger var) {
    return orNull(ranges, (range) -> toBDD(range, var));
  }

  @Nullable
  private BDD toBDD(@Nullable Set<SubRange> ranges, BDDIcmpCode var) {
    return orNull(ranges, (range) -> toBDD(range, var));
  }

  @Nullable
  private BDD toBDD(@Nullable Set<SubRange> ranges, BDDIcmpType var) {
    return orNull(ranges, (range) -> toBDD(range, var));
  }

  private BDD toBDD(@Nullable Set<SubRange> packetLengths, BDDPacketLength packetLength) {
    return orNull(packetLengths, (range) -> toBDD(range, packetLength));
  }

  private static BDD toBDD(SubRange range, BDDIcmpCode var) {
    int start = range.getStart();
    int end = range.getEnd();
    return start == end ? var.value(start) : var.geq(start).and(var.leq(end));
  }

  private static BDD toBDD(SubRange range, BDDIcmpType var) {
    int start = range.getStart();
    int end = range.getEnd();
    return start == end ? var.value(start) : var.geq(start).and(var.leq(end));
  }

  private static BDD toBDD(SubRange range, BDDPacketLength packetLength) {
    int start = range.getStart();
    int end = range.getEnd();
    return start == end ? packetLength.value(start) : packetLength.range(start, end);
  }

  private static BDD toBDD(SubRange range, BDDInteger var) {
    return var.range(range.getStart(), range.getEnd());
  }

  private BDD toBDD(List<TcpFlagsMatchConditions> tcpFlags) {
    return orNull(tcpFlags, this::toBDD);
  }

  /** For TcpFlagsMatchConditions */
  private @Nonnull BDD toBDD(boolean flagValue, BDD flagBDD) {
    return flagValue ? flagBDD : flagBDD.not();
  }

  private @Nonnull BDD toBDD(TcpFlagsMatchConditions tcpFlags) {
    int numFlags =
        (tcpFlags.getUseAck() ? 1 : 0)
            + (tcpFlags.getUseCwr() ? 1 : 0)
            + (tcpFlags.getUseEce() ? 1 : 0)
            + (tcpFlags.getUseFin() ? 1 : 0)
            + (tcpFlags.getUsePsh() ? 1 : 0)
            + (tcpFlags.getUseRst() ? 1 : 0)
            + (tcpFlags.getUseSyn() ? 1 : 0)
            + (tcpFlags.getUseUrg() ? 1 : 0);
    if (numFlags == 0) {
      return _bddFactory.one();
    }

    // note: order must match bdd variable order
    BDD[] literals = new BDD[numFlags];
    int i = 0;
    if (tcpFlags.getUseAck()) {
      literals[i++] = toBDD(tcpFlags.getTcpFlags().getAck(), _bddPacket.getTcpAck());
    }
    if (tcpFlags.getUseCwr()) {
      literals[i++] = toBDD(tcpFlags.getTcpFlags().getCwr(), _bddPacket.getTcpCwr());
    }
    if (tcpFlags.getUseEce()) {
      literals[i++] = toBDD(tcpFlags.getTcpFlags().getEce(), _bddPacket.getTcpEce());
    }
    if (tcpFlags.getUseFin()) {
      literals[i++] = toBDD(tcpFlags.getTcpFlags().getFin(), _bddPacket.getTcpFin());
    }
    if (tcpFlags.getUsePsh()) {
      literals[i++] = toBDD(tcpFlags.getTcpFlags().getPsh(), _bddPacket.getTcpPsh());
    }
    if (tcpFlags.getUseRst()) {
      literals[i++] = toBDD(tcpFlags.getTcpFlags().getRst(), _bddPacket.getTcpRst());
    }
    if (tcpFlags.getUseSyn()) {
      literals[i++] = toBDD(tcpFlags.getTcpFlags().getSyn(), _bddPacket.getTcpSyn());
    }
    if (tcpFlags.getUseUrg()) {
      literals[i++] = toBDD(tcpFlags.getTcpFlags().getUrg(), _bddPacket.getTcpUrg());
    }
    assert i == literals.length;
    return _bddFactory.andLiterals(literals);
  }

  private static void addIfNonNull(List<BDD> bdds, @Nullable BDD bdd) {
    if (bdd != null) {
      bdds.add(bdd);
    }
  }

  public BDD toBDD(HeaderSpace headerSpace) {
    // HeaderSpace has null fields to mean "unconstrained". Thus we must be careful to treat these
    // as correctly "everything" when positive or "nothing" when negated.
    List<BDD> bdds = new ArrayList<>();

    addIfNonNull(bdds, toBDD(headerSpace.getPacketLengths(), _bddPacket.getPacketLength()));
    addIfNonNull(
        bdds,
        negateIfNonNull(toBDD(headerSpace.getNotPacketLengths(), _bddPacket.getPacketLength())));
    addIfNonNull(bdds, toBDD(headerSpace.getFragmentOffsets(), _bddPacket.getFragmentOffset()));

    addIfNonNull(
        bdds,
        negateIfNonNull(
            toBDD(headerSpace.getNotFragmentOffsets(), _bddPacket.getFragmentOffset())));
    addIfNonNull(bdds, toBDD(headerSpace.getEcns(), _bddPacket.getEcn()));
    addIfNonNull(bdds, negateIfNonNull(toBDD(headerSpace.getNotEcns(), _bddPacket.getEcn())));
    addIfNonNull(bdds, toBDD(headerSpace.getDscps(), _bddPacket.getDscp()));
    addIfNonNull(bdds, negateIfNonNull(toBDD(headerSpace.getNotDscps(), _bddPacket.getDscp())));
    addIfNonNull(bdds, toBDD(headerSpace.getTcpFlags()));
    addIfNonNull(bdds, toBDD(headerSpace.getIcmpTypes(), _bddPacket.getIcmpType()));
    addIfNonNull(
        bdds, negateIfNonNull(toBDD(headerSpace.getNotIcmpTypes(), _bddPacket.getIcmpType())));
    addIfNonNull(bdds, toBDD(headerSpace.getIcmpCodes(), _bddPacket.getIcmpCode()));
    addIfNonNull(
        bdds, negateIfNonNull(toBDD(headerSpace.getNotIcmpCodes(), _bddPacket.getIcmpCode())));
    addIfNonNull(bdds, toBDD(headerSpace.getIpProtocols()));
    addIfNonNull(bdds, negateIfNonNull(toBDD(headerSpace.getNotIpProtocols())));
    addIfNonNull(bdds, toBDD(headerSpace.getSrcPorts(), _bddPacket.getSrcPort()));
    addIfNonNull(
        bdds, negateIfNonNull(toBDD(headerSpace.getNotSrcPorts(), _bddPacket.getSrcPort())));
    addIfNonNull(
        bdds,
        orWithNull(
            toBDD(headerSpace.getSrcOrDstPorts(), _bddPacket.getSrcPort()),
            toBDD(headerSpace.getSrcOrDstPorts(), _bddPacket.getDstPort())));
    addIfNonNull(bdds, toBDD(headerSpace.getDstPorts(), _bddPacket.getDstPort()));
    addIfNonNull(
        bdds, negateIfNonNull(toBDD(headerSpace.getNotDstPorts(), _bddPacket.getDstPort())));
    addIfNonNull(bdds, toBDD(headerSpace.getSrcIps(), _srcIpSpaceToBdd));
    addIfNonNull(bdds, negateIfNonNull(toBDD(headerSpace.getNotSrcIps(), _srcIpSpaceToBdd)));
    addIfNonNull(
        bdds,
        orWithNull(
            toBDD(headerSpace.getSrcOrDstIps(), _srcIpSpaceToBdd),
            toBDD(headerSpace.getSrcOrDstIps(), _dstIpSpaceToBdd)));
    addIfNonNull(bdds, toBDD(headerSpace.getDstIps(), _dstIpSpaceToBdd));
    addIfNonNull(bdds, negateIfNonNull(toBDD(headerSpace.getNotDstIps(), _dstIpSpaceToBdd)));
    BDD res = _bddFactory.andAllAndFree(bdds);
    if (headerSpace.getNegate()) {
      res.notEq();
    }
    return res;
  }
}
