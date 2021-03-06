package com.github.javactic.futures;

import com.github.javactic.Bad;
import com.github.javactic.Every;
import com.github.javactic.Fail;
import com.github.javactic.Good;
import com.github.javactic.One;
import com.github.javactic.Or;
import com.github.javactic.Pass;
import com.github.javactic.Validation;
import javaslang.Tuple;
import javaslang.Tuple2;
import javaslang.collection.Iterator;
import javaslang.collection.List;
import javaslang.collection.Seq;
import javaslang.collection.Vector;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


@RunWith(Theories.class)
public class AccumulationTest {
  private FutureFactory<String> ff = FutureFactory.OF_EXCEPTION_MESSAGE;
  private FutureFactory<One<String>> ffAcc = ff.accumulating();

  @DataPoints
  public static Executor[] configs = {Executors.newSingleThreadExecutor(), Helper.DEFAULT_EXECUTOR};

  @Test
  public void withGoodFail() throws Exception {
    OrFuture<String, One<String>> success = OrFuture.ofGood("success");
    OrFuture<String, One<String>> fail = OrFuture.ofOneBad("failure");
    OrFuture<String, Every<String>> result = OrFuture.withGood(success, fail, (a1, a2) -> "doesn't matter");
    Or<String, Every<String>> or = result.get(Duration.ofSeconds(10));
    assertEquals("failure", or.getBad().head());
  }

  @Test
  public void withGoodSuccess() throws Exception {
    OrFuture<String, One<String>> s1 = OrFuture.ofGood("A");
    OrFuture<Integer, One<String>> s2 = OrFuture.ofGood(1);
    OrFuture<String, Every<String>> result = OrFuture.withGood(s1, s2, (a1, a2) -> a1 + a2);
    Or<String, Every<String>> or = result.get(Duration.ofSeconds(10));
    assertEquals("A1", or.get());
  }

  @Test
  public void sequenceIterator() {
    OrFuture<String, One<String>> f1 = ff.newFuture(() -> Good.of("1")).accumulating();
    OrFuture<String, One<String>> f2 = ff.newFuture(() -> Bad.<String,String>of("2")).accumulating();
    OrFuture<Vector<String>, Every<String>> sequence = OrFuture.sequence(Iterator.of(f1, f2));
    Or<Vector<String>, Every<String>> or = sequence.getUnsafe();
    String fold = or.getBad().foldLeft("", (s, i) -> s + i);
    assertEquals("2", fold);
  }

  @Theory
  public void sequenceSuccess(Executor es) throws Exception {
    Seq<OrFuture<Integer, One<String>>> seq = Vector.empty();
    for (int i = 0; i < 10; i++) {
      final int fi = i;
      seq = seq.append(ff.newFuture(es, () -> Good.of(fi)).accumulating());
    }
    OrFuture<Vector<Integer>, Every<String>> sequence = OrFuture.sequence(seq);
    Or<Vector<Integer>, Every<String>> or = sequence.get(Duration.ofSeconds(10));
    Assert.assertTrue(or.isGood());
    String fold = or.get().foldLeft("", (s, i) -> s + i);
    assertEquals("0123456789", fold);
  }

  @Theory
  public void sequenceFailure(Executor es) throws Exception {
    Seq<OrFuture<Integer, One<String>>> seq = Vector.empty();
    for (int i = 0; i < 10; i++) {
      final int fi = i;
      if (i % 2 == 0)
        seq = seq.append(ffAcc.newFuture(es, () -> Good.of(fi)));
      else
        seq = seq.append(ffAcc.newFuture(es, () -> Bad.ofOne(fi+ "")));
    }
    OrFuture<Vector<Integer>, Every<String>> sequence = OrFuture.sequence(seq);
    Or<Vector<Integer>, Every<String>> or = sequence.get(Duration.ofSeconds(10));
    Assert.assertTrue(or.isBad());
    String fold = or.getBad().foldLeft("", (s, i) -> s + i);
    assertEquals(1, fold.length());
  }

  @Test
  public void combinedIterator() {
    OrFuture<String, One<String>> f1 = ff.newFuture(() -> Good.of("1")).accumulating();
    OrFuture<String, One<String>> f2 = ff.newFuture(() -> Good.of("2")).accumulating();
    OrFuture<Vector<String>, Every<String>> combined = OrFuture.combined(Iterator.of(f1, f2));
    Or<Vector<String>, Every<String>> or = combined.getUnsafe();
    String fold = or.get().foldLeft("", (s, i) -> s + i);
    assertEquals("12", fold);
  }

  @Test
  public void combinedSecondFinishesFirst() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    OrFuture<String, One<String>> f1 = ff.newFuture(() -> {
      try {
        latch.await();
      } catch (Exception e) {
        Assert.fail();
      }
      return Good.of("1");
    }).accumulating();
    OrFuture<String, One<String>> f2 = ff.newFuture(() -> Good.of("2")).accumulating();
    OrFuture<String, One<String>> f3 = ff.newFuture(() -> Good.of("3")).accumulating();
    OrFuture<String, One<String>> f4 = ff.newFuture(() -> Good.of("4")).accumulating();
    OrFuture<Vector<String>, Every<String>> combined = OrFuture.combined(Vector.of(f1, f2, f3, f4));
    f4.onComplete(or -> latch.countDown());
    Or<Vector<String>, Every<String>> or = combined.get(Duration.ofSeconds(10));
    Assert.assertTrue(or.isGood());
    String fold = or.get().foldLeft("", (s, i) -> s + i);
    assertEquals("1234", fold);
  }

  @Test
  public void combinedSecondFinishesLast() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    OrFuture<String, One<String>> f1 = OrFuture.of(() -> Good.<String,String>of("1")).accumulating();
    OrFuture<String, One<String>> f2 = ff.newFuture(() -> Good.of("2")).accumulating();
    OrFuture<String, One<String>> f3 = ff.newFuture(() -> Good.of("3")).accumulating();
    OrFuture<String, One<String>> f4 = ff.newFuture(() -> {
      try {
        latch.await();
      } catch (Exception e) {
        Assert.fail();
      }
      return Good.of("4");
    }).accumulating();
    OrFuture<Vector<String>, Every<String>> combined = OrFuture.combined(Vector.of(f1, f2, f3, f4));
    f1.onComplete(or -> latch.countDown());
    Or<Vector<String>, Every<String>> or = combined.get(Duration.ofSeconds(10));
    Assert.assertTrue(or.isGood());
    String fold = or.get().foldLeft("", (s, i) -> s + i);
    assertEquals("1234", fold);
  }

  @Test
  public void combined() throws TimeoutException, InterruptedException {
    Executor es = Helper.DEFAULT_EXECUTOR;
    int total = 0;
    for (int i = 0; i < 50; i++) {
      total += testCombined(es);
    }
  }

  private int testCombined(Executor es) throws TimeoutException, InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    int size = ThreadLocalRandom.current().nextInt(40,100);
    Vector<OrFuture<String, One<String>>> vector = Vector.empty();
    for (int i = 0; i < size; i++) {
      vector = vector.append(createRandomWaitingFuture(es, latch));
    }
    vector = vector.append(ff.newFuture(() -> Good.of("direct")).accumulating());
    OrFuture<Vector<String>, Every<String>> combined = OrFuture.combined(vector);
    vector.last().onComplete(stringOneOr -> {
      latch.countDown();
    });
    Or<Vector<String>, Every<String>> or = combined.get(Duration.ofSeconds(20));
    Assert.assertTrue(or.isGood());
    return size;
  }

  private OrFuture<String, One<String>> createRandomWaitingFuture(Executor es, CountDownLatch latch) {
    if (ThreadLocalRandom.current().nextBoolean()) {
      return ff.newFuture(es, () -> {
        try {
          latch.await();
        } catch (Exception e) {
          Assert.fail();
        }
        return Good.of("waiting");
      }).accumulating();
    } else {
      return OrFuture.<String, String>of(es, () -> Good.<String, String>of("direct")).accumulating();
    }
  }

  @Theory
  public void validatedBy(Executor es) throws InterruptedException, ExecutionException, TimeoutException {
    Vector<Integer> vec = Vector.of(1,2,3,4);
    Function<Integer, OrFuture<Integer, One<String>>> f = i ->
      ffAcc.newFuture(es, () -> {
        if(i < 10) return Good.of(i);
        else return Bad.ofOne("wasn't under 10");
      });
    Or<Vector<Integer>, Every<String>> res = OrFuture.validatedBy(vec, f).get(Duration.ofSeconds(10));
    assertTrue(res.isGood());
    assertEquals(vec, res.get());
    res = OrFuture.validatedBy(Vector.of(11), f, Vector.collector()).get(Duration.ofSeconds(10));
    assertTrue(res.isBad());
    assertTrue(res.getBad() instanceof One);
  }

  @Theory
  public void when(Executor es) throws InterruptedException, ExecutionException, TimeoutException {
    Function<String, Validation<String>> f1 = f -> f.startsWith("s") ? Pass.instance() : Fail.of("does not start with s");
    Function<String, Validation<String>> f2 = f -> f.length() > 4 ? Fail.of("too long") : Pass.instance();
    OrFuture<String, One<String>> orFuture = ff.newFuture(es, () -> Bad.<String,String>of("failure")).accumulating();
    OrFuture<String, Every<String>> res = OrFuture.when(orFuture, f1, f2);
    assertEquals("failure", res.get(Duration.ofSeconds(10)).getBad().get(0));
    orFuture = ff.newFuture(es, () -> Good.of("sub")).accumulating();
    res = OrFuture.when(orFuture, f1, f2);
    assertTrue(res.get(Duration.ofSeconds(10)).isGood());
    orFuture = ff.newFuture(es, () -> Good.of("fubiluuri")).accumulating();
    res = OrFuture.when(orFuture, f1, f2);
    assertTrue(res.get(Duration.ofSeconds(10)).isBad());
  }

  @Theory
  public void withGood(Executor es) throws Exception {
    Function<? super OrFuture<String, ? extends Every<String>>[], OrFuture<?, Every<String>>> fun =
      ors -> OrFuture.withGood(ors[0], ors[1], (a, b) -> "");
    testWithF(es, fun, 2);
    fun = o -> OrFuture.withGood(o[0], o[1], o[2], (a, b, c) -> "");
    testWithF(es, fun, 3);
    fun = o -> OrFuture.withGood(o[0], o[1], o[2], o[3], (a, b, c, d) -> "");
    testWithF(es, fun, 4);
    fun = o -> OrFuture.withGood(o[0], o[1], o[2], o[3], o[4], (a, b, c, d, e) -> "");
    testWithF(es, fun, 5);
    fun = o -> OrFuture.withGood(o[0], o[1], o[2], o[3], o[4], o[5], (a, b, c, d, e, f) -> "");
    testWithF(es, fun, 6);
    fun = o -> OrFuture.withGood(o[0], o[1], o[2], o[3], o[4], o[5], o[6], (a, b, c, d, e, f, g) -> "");
    testWithF(es, fun, 7);
    fun = o -> OrFuture.withGood(o[0], o[1], o[2], o[3], o[4], o[5], o[6], o[7], (a, b, c, d, e, f, g, h) -> "");
    testWithF(es, fun, 8);
  }

  @Theory
  public void zips(Executor es) throws Exception {
    Function<? super OrFuture<String, ? extends Every<String>>[], OrFuture<?, Every<String>>> fun =
      ors -> OrFuture.zip(ors[0], ors[1]);
    testWithF(es, fun, 2);
    fun = o -> OrFuture.zip3(o[0], o[1], o[2]);
    testWithF(es, fun, 3);
  }

  private void testWithF(Executor es,
                         Function<? super OrFuture<String, ? extends Every<String>>[], OrFuture<?, Every<String>>> f,
                         int size) throws Exception {
    @SuppressWarnings("unchecked")
    OrFuture<String, One<String>>[] ors = new OrFuture[size];
    for (int i = 0; i <= ors.length; i++) {
      for (int j = 0; j < ors.length; j++) {
        if (j == i) ors[j] = ff.newFuture(es, () -> Bad.<String,String>of("failure")).accumulating();
        else ors[j] = ffAcc.newFuture(es, () -> Good.of("success"));
      }
      OrFuture<?, Every<String>> val = f.apply(ors);
      if (i < ors.length)
        assertTrue(val.get(Duration.ofSeconds(10)).isBad());
      else
        assertTrue(val.get(Duration.ofSeconds(10)).isGood());
    }
  }

  @Theory
  public void sequenceWithErrors(Executor es) throws TimeoutException, InterruptedException {
    Tuple2<Iterable<OrFuture<String, One<String>>>, AtomicInteger> t2 = iterable(es, 1000, true);
    OrFuture<Vector<String>, Every<String>> withErrorsF = OrFuture.sequence(t2._1);
    Or<Vector<String>, Every<String>> withErrors = withErrorsF.get(Duration.ofSeconds(10));
    if(t2._2.get() > 0) {
      Assert.assertTrue(withErrors.isBad());
    } else {
      Assert.assertTrue(withErrors.isGood());
    }
  }

  @Theory
  public void sequenceWithoutErrors(Executor es) throws TimeoutException, InterruptedException {
    int COUNT = 1000;
    Tuple2<Iterable<OrFuture<String, One<String>>>, AtomicInteger> t2 = iterable(es, COUNT, false);
    OrFuture<Vector<String>, Every<String>> withoutErrorsF = OrFuture.sequence(t2._1);
    Or<Vector<String>, Every<String>> withoutErrors = withoutErrorsF.get(Duration.ofSeconds(10));
    Assert.assertEquals(0, t2._2.get());
    Assert.assertTrue(withoutErrors.isGood());
    Assert.assertEquals(COUNT, withoutErrors.get().length());
  }

  private Tuple2<Iterable<OrFuture<String, One<String>>>, AtomicInteger> iterable(Executor es, int count, boolean errors) {
    List<OrFuture<String, One<String>>> list = List.empty();
    AtomicInteger errorCount = new AtomicInteger();
    for (int i = 0; i < count; i++) {
      int value = i;
      OrFuture<String, One<String>> future = OrFuture.of(es, () -> {
        if (errors && ThreadLocalRandom.current().nextBoolean()) {
          errorCount.incrementAndGet();
          return Bad.ofOne("bad " + value);
        } else {
          return Or.good("good " + value);
        }
      });
      list = list.prepend(future);
    }
    return Tuple.of(list, errorCount);
  }

  @Test
  public void sequence() {
    CountDownLatch second = new CountDownLatch(1);
    Iterable<OrFuture<String, One<String>>> iterable = Vector.of(getBad(new CountDownLatch(0)), getBad(second));
    OrFuture<Vector<String>, Every<String>> sequence = OrFuture.sequence(iterable);
    try {
      Or<Vector<String>, Every<String>> or = sequence.get(Duration.ofSeconds(10));
      Assert.assertTrue(or.isBad());
    } catch (TimeoutException | InterruptedException e) {
      Assert.fail(e.getMessage());
    } finally {
      second.countDown();
    }
  }

  private OrFuture<String, One<String>> getBad(CountDownLatch latch) {
    return OrFuture.of(() -> {
      try {
        latch.await();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      return Bad.ofOne("bad");
    });
  }

  @Test
  public void firstCompletedOf() {
    CountDownLatch first = new CountDownLatch(1);
    Iterable<OrFuture<String, String>> iterable = Vector.of(getGood(first, "a"), getGood(new CountDownLatch(0), "b"));
    OrFuture<String, String> completed = OrFuture.firstCompletedOf(iterable);
    try {
      Or<String, String> or = completed.get(Duration.ofSeconds(10));
      Assert.assertEquals("b", or.get());
    } catch (TimeoutException | InterruptedException e) {
      Assert.fail(e.getMessage());
    } finally {
      first.countDown();
    }
  }

  private OrFuture<String, String> getGood(CountDownLatch latch, String value) {
    return OrFuture.of(() -> {
      try {
        latch.await();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      return Good.of(value);
    });
  }

  @Test
  public void constructorsForCoverage() throws Exception {
    Constructor<Helper> constructor = Helper.class.getDeclaredConstructor();
    assertTrue(Modifier.isPrivate(constructor.getModifiers()));
    constructor.setAccessible(true);
    constructor.newInstance();
  }

}
