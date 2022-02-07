package org.batfish.representation.frr;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.batfish.datamodel.routing_policy.expr.ExplicitAs;
import org.batfish.datamodel.routing_policy.expr.LiteralAsList;
import org.batfish.datamodel.routing_policy.statement.PrependAsPath;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.junit.Test;

/** Test of {@link RouteMapSetPrependAsPath} */
public class RouteMapSetPrependAsPathTest {

  @Test
  public void testToStatements() {
    RouteMapSetPrependAsPath set = new RouteMapSetPrependAsPath(ImmutableList.of(1L, 2L, 3L));
    List<Statement> result =
        set.toStatements(null, null, null).collect(ImmutableList.toImmutableList());
    assertThat(
        result,
        contains(
            new PrependAsPath(
                new LiteralAsList(
                    ImmutableList.of(
                        new ExplicitAs(1L), new ExplicitAs(2L), new ExplicitAs(3L))))));
  }
}
