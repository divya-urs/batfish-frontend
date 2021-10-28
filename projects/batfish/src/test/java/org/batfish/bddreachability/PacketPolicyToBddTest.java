package org.batfish.bddreachability;

import static org.batfish.bddreachability.EdgeMatchers.edge;
import static org.batfish.bddreachability.transition.Transitions.ZERO;
import static org.batfish.bddreachability.transition.Transitions.constraint;
import static org.batfish.bddreachability.transition.Transitions.eraseAndSet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.List;
import net.sf.javabdd.BDD;
import org.batfish.bddreachability.IpsRoutedOutInterfacesFactory.IpsRoutedOutInterfaces;
import org.batfish.bddreachability.PacketPolicyToBdd.BoolExprToBdd;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.common.bdd.BDDSourceManager;
import org.batfish.common.bdd.IpAccessListToBdd;
import org.batfish.common.bdd.IpAccessListToBddImpl;
import org.batfish.common.bdd.IpSpaceToBDD;
import org.batfish.datamodel.AclLine;
import org.batfish.datamodel.ConnectedRoute;
import org.batfish.datamodel.ExprAclLine;
import org.batfish.datamodel.Fib;
import org.batfish.datamodel.FibEntry;
import org.batfish.datamodel.FibForward;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.MockFib;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.UniverseIpSpace;
import org.batfish.datamodel.acl.MatchHeaderSpace;
import org.batfish.datamodel.packet_policy.ApplyFilter;
import org.batfish.datamodel.packet_policy.ApplyTransformation;
import org.batfish.datamodel.packet_policy.Conjunction;
import org.batfish.datamodel.packet_policy.Drop;
import org.batfish.datamodel.packet_policy.FalseExpr;
import org.batfish.datamodel.packet_policy.FibLookup;
import org.batfish.datamodel.packet_policy.FibLookupOutgoingInterfaceIsOneOf;
import org.batfish.datamodel.packet_policy.If;
import org.batfish.datamodel.packet_policy.LiteralVrfName;
import org.batfish.datamodel.packet_policy.PacketMatchExpr;
import org.batfish.datamodel.packet_policy.PacketPolicy;
import org.batfish.datamodel.packet_policy.Return;
import org.batfish.datamodel.packet_policy.TrueExpr;
import org.batfish.datamodel.transformation.Transformation;
import org.batfish.datamodel.transformation.TransformationStep;
import org.batfish.symbolic.state.PacketPolicyAction;
import org.batfish.symbolic.state.PacketPolicyStatement;
import org.junit.Before;
import org.junit.Test;

/** Tests of {@link PacketPolicyToBdd} */
public final class PacketPolicyToBddTest {
  private static final IpsRoutedOutInterfaces EMPTY_IPS_ROUTED_OUT_INTERFACES =
      new IpsRoutedOutInterfaces(MockFib.builder().build());

  private BDDPacket _bddPacket;
  private IpAccessListToBdd _ipAccessListToBdd;

  private final String _hostname = "hostname";
  private final String _policyName = "policy";

  private PacketPolicyStatement statement(int id) {
    return new PacketPolicyStatement(_hostname, _policyName, id);
  }

  private PacketPolicyAction fibLookupState(String vrfName) {
    return new PacketPolicyAction(
        _hostname, _policyName, new FibLookup(new LiteralVrfName(vrfName)));
  }

  @Before
  public void setUp() {
    _bddPacket = new BDDPacket();
    _ipAccessListToBdd =
        new IpAccessListToBddImpl(
            _bddPacket, BDDSourceManager.empty(_bddPacket), ImmutableMap.of(), ImmutableMap.of());
  }

  private final PacketPolicyAction _dropState =
      new PacketPolicyAction(_hostname, _policyName, Drop.instance());

  @Test
  public void testDefaultAction() {
    List<Edge> edges =
        PacketPolicyToBdd.evaluate(
            _hostname,
            new PacketPolicy(_policyName, ImmutableList.of(), new Return(Drop.instance())),
            _ipAccessListToBdd,
            EMPTY_IPS_ROUTED_OUT_INTERFACES);
    // Everything is dropped
    assertThat(edges, contains(edge(statement(0), _dropState)));
  }

  @Test
  public void testReturn() {
    List<Edge> edges =
        PacketPolicyToBdd.evaluate(
            _hostname,
            new PacketPolicy(
                _policyName,
                ImmutableList.of(new Return(new FibLookup(new LiteralVrfName("vrf")))),
                new Return(Drop.instance())),
            _ipAccessListToBdd,
            EMPTY_IPS_ROUTED_OUT_INTERFACES);
    // Everything is looked up in "vrf"
    assertThat(edges, contains(edge(statement(0), fibLookupState("vrf"))));
  }

  @Test
  public void testNestedIfs() {
    String vrf1 = "vrf1";
    String vrf2 = "vrf2";
    String vrf3 = "vrf3";

    Prefix dstIps = Prefix.parse("10.0.0.0/8");
    If innerIf =
        new If(
            new PacketMatchExpr(
                new MatchHeaderSpace(HeaderSpace.builder().setDstIps(dstIps.toIpSpace()).build())),
            ImmutableList.of(new Return(new FibLookup(new LiteralVrfName(vrf1)))));
    If outerIf =
        new If(
            new PacketMatchExpr(
                new MatchHeaderSpace(
                    HeaderSpace.builder().setDstIps(UniverseIpSpace.INSTANCE).build())),
            ImmutableList.of(innerIf, new Return(new FibLookup(new LiteralVrfName(vrf2)))));
    List<Edge> edges =
        PacketPolicyToBdd.evaluate(
            _hostname,
            new PacketPolicy(
                _policyName,
                ImmutableList.of(outerIf, new Return(new FibLookup(new LiteralVrfName(vrf3)))),
                new Return(Drop.instance())),
            _ipAccessListToBdd,
            EMPTY_IPS_ROUTED_OUT_INTERFACES);

    BDD dstIpBdd = new IpSpaceToBDD(_bddPacket.getDstIp()).toBDD(dstIps);

    assertThat(
        edges,
        containsInAnyOrder(
            // outer if
            edge(statement(0), statement(1)),
            // inner if then branch
            edge(statement(1), statement(2), constraint(dstIpBdd)),
            // return vrf1
            edge(statement(2), fibLookupState(vrf1)),
            // inner if fall-through
            edge(statement(1), statement(3), constraint(dstIpBdd.not())),
            // return vrf2
            edge(statement(3), fibLookupState(vrf2)),
            // outer if fall-through
            edge(statement(0), statement(4), ZERO),
            // return vrf3
            edge(statement(4), fibLookupState(vrf3))));
  }
  // TODO test edge to default action is generated iff fall-through

  @Test
  public void testApplyFilter() {
    // Set up an ACL that permits traffic to 1.1.1.0/24
    Prefix permittedPrefix = Prefix.parse("1.1.1.0/24");
    AclLine line =
        new ExprAclLine(
            LineAction.PERMIT,
            new MatchHeaderSpace(
                HeaderSpace.builder().setDstIps(permittedPrefix.toIpSpace()).build()),
            "foo");
    IpAccessList acl = IpAccessList.builder().setName("acl").setLines(line).build();
    IpAccessListToBdd ipAccessListToBdd =
        new IpAccessListToBddImpl(
            _bddPacket,
            BDDSourceManager.empty(_bddPacket),
            ImmutableMap.of(acl.getName(), acl),
            ImmutableMap.of());

    // Evaluate a PacketPolicy that uses an ApplyFilter for the above ACL
    FibLookup fl = new FibLookup(new LiteralVrfName("vrf"));
    PacketPolicy policy =
        new PacketPolicy("name", ImmutableList.of(new ApplyFilter(acl.getName())), new Return(fl));
    List<Edge> edges =
        PacketPolicyToBdd.evaluate(
            _hostname, policy, ipAccessListToBdd, EMPTY_IPS_ROUTED_OUT_INTERFACES);

    // Traffic not destined for 1.1.1.0/24 should be dropped
    BDD permitted = _bddPacket.getDstIpSpaceToBDD().toBDD(permittedPrefix);
    //    assertThat(evaluator.getFibLookups().get(fl), mapsOne(permitted));
    //    assertThat(evaluator.getToDrop(), mapsOne(permitted.not()));
  }

  @Test
  public void testApplyTransformation() {
    FibLookup fl = new FibLookup(new LiteralVrfName("vrf"));
    Ip ip = Ip.parse("8.8.8.8");
    Transformation transformation =
        Transformation.always().apply(TransformationStep.assignSourceIp(ip, ip)).build();
    List<Edge> edges =
        PacketPolicyToBdd.evaluate(
            _hostname,
            new PacketPolicy(
                _policyName,
                ImmutableList.of(new ApplyTransformation(transformation), new Return(fl)),
                new Return(Drop.instance())),
            _ipAccessListToBdd,
            EMPTY_IPS_ROUTED_OUT_INTERFACES);
    BDD ipBdd = _bddPacket.getSrcIpSpaceToBDD().toBDD(ip);
    assertThat(
        edges,
        containsInAnyOrder(
            edge(statement(0), statement(1), eraseAndSet(_bddPacket.getSrcIp(), ipBdd)),
            edge(statement(1), fibLookupState("vrf"))));
  }

  @Test
  public void testFibLookupOutgoingInterfaceIsOneOf() {
    String iface1 = "iface1";
    String iface2 = "iface2";
    FibLookupOutgoingInterfaceIsOneOf expr =
        new FibLookupOutgoingInterfaceIsOneOf(
            new LiteralVrfName("vrf"), ImmutableList.of(iface1, iface2));

    Prefix prefix1 = Prefix.parse("1.2.3.0/24");
    Prefix prefix2 = Prefix.parse("2.2.3.0/24");
    ConnectedRoute route1 = new ConnectedRoute(prefix1, iface1);
    ConnectedRoute route2 = new ConnectedRoute(prefix2, iface2);
    BDD prefix1Bdd = _bddPacket.getDstIpSpaceToBDD().toBDD(prefix1);
    BDD prefix2Bdd = _bddPacket.getDstIpSpaceToBDD().toBDD(prefix2);

    // empty fib
    {
      Fib fib = MockFib.builder().build();
      IpsRoutedOutInterfaces ipsRoutedOutInterfaces = new IpsRoutedOutInterfaces(fib);
      BoolExprToBdd toBdd = new BoolExprToBdd(_ipAccessListToBdd, ipsRoutedOutInterfaces);
      assertTrue(toBdd.visit(expr).isZero());
    }

    // single fib entry with missing matching Ips
    {
      Fib fib =
          MockFib.builder()
              .setFibEntries(
                  ImmutableMap.of(
                      Ip.ZERO,
                      ImmutableSet.of(
                          new FibEntry(new FibForward(Ip.ZERO, iface1), ImmutableList.of(route1)))))
              .build();
      IpsRoutedOutInterfaces ipsRoutedOutInterfaces = new IpsRoutedOutInterfaces(fib);
      BoolExprToBdd toBdd = new BoolExprToBdd(_ipAccessListToBdd, ipsRoutedOutInterfaces);
      assertTrue(toBdd.visit(expr).isZero());
    }

    // single fib entry with matching Ips
    {
      Fib fib =
          MockFib.builder()
              .setFibEntries(
                  ImmutableMap.of(
                      Ip.ZERO,
                      ImmutableSet.of(
                          new FibEntry(new FibForward(Ip.ZERO, iface1), ImmutableList.of(route1)))))
              .setMatchingIps(ImmutableMap.of(prefix1, prefix1.toIpSpace()))
              .build();
      IpsRoutedOutInterfaces ipsRoutedOutInterfaces = new IpsRoutedOutInterfaces(fib);
      BoolExprToBdd toBdd = new BoolExprToBdd(_ipAccessListToBdd, ipsRoutedOutInterfaces);
      assertEquals(prefix1Bdd, toBdd.visit(expr));
    }

    // two fib entries
    {
      Fib fib =
          MockFib.builder()
              .setFibEntries(
                  ImmutableMap.of(
                      Ip.ZERO,
                      ImmutableSet.of(
                          new FibEntry(new FibForward(Ip.ZERO, iface1), ImmutableList.of(route1)),
                          new FibEntry(new FibForward(Ip.ZERO, iface2), ImmutableList.of(route2)))))
              .setMatchingIps(
                  ImmutableMap.of(prefix1, prefix1.toIpSpace(), prefix2, prefix2.toIpSpace()))
              .build();
      IpsRoutedOutInterfaces ipsRoutedOutInterfaces = new IpsRoutedOutInterfaces(fib);
      BoolExprToBdd toBdd = new BoolExprToBdd(_ipAccessListToBdd, ipsRoutedOutInterfaces);
      assertEquals(prefix1Bdd.or(prefix2Bdd), toBdd.visit(expr));
    }
  }

  @Test
  public void testConjunction() {
    FibLookup fl = new FibLookup(new LiteralVrfName("vrf"));
    {
      List<Edge> edges =
          PacketPolicyToBdd.evaluate(
              _hostname,
              new PacketPolicy(
                  _policyName,
                  ImmutableList.of(
                      new If(
                          Conjunction.of(TrueExpr.instance()),
                          Collections.singletonList(new Return(fl)))),
                  new Return(Drop.instance())),
              _ipAccessListToBdd,
              EMPTY_IPS_ROUTED_OUT_INTERFACES);
      assertThat(
          edges,
          contains(
              edge(statement(0), statement(1)),
              edge(statement(1), fibLookupState("vrf")),
              edge(statement(0), statement(2), ZERO),
              edge(statement(2), _dropState)));
    }

    {
      List<Edge> edges =
          PacketPolicyToBdd.evaluate(
              _hostname,
              new PacketPolicy(
                  _policyName,
                  ImmutableList.of(
                      new If(
                          Conjunction.of(TrueExpr.instance(), FalseExpr.instance()),
                          Collections.singletonList(new Return(fl)))),
                  new Return(Drop.instance())),
              _ipAccessListToBdd,
              EMPTY_IPS_ROUTED_OUT_INTERFACES);
      assertThat(
          edges,
          contains(
              edge(statement(0), statement(1), ZERO),
              edge(statement(1), fibLookupState("vrf")),
              edge(statement(0), statement(2)),
              edge(statement(2), _dropState)));
    }
  }
}
