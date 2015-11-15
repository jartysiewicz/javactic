/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.mvh77.javactic;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javaslang.control.Either;
import javaslang.control.Failure;
import javaslang.control.Left;
import javaslang.control.Option;
import javaslang.control.Try;

public class Bad<G,B> implements Or<G,B> {
	
	final B value;
	
	Bad(B bad) {
		value = bad;
	}
	
	public static <G,B> Bad<G,B> of(B value) {
		return new Bad<>(value);
	}

	@Override
	public Or<G, Every<B>> accumulating() {
		return Or.bad(Every.of(value));
	}

	@SuppressWarnings("unchecked")
	@Override
	public <H> Or<H, B> map(Function<? super G, ? extends H> mapper) {
		return (Or<H, B>) this;
	}

	@Override
	public <C> Or<G, C> badMap(Function<? super B, ? extends C> mapper) {
		return Or.bad(mapper.apply(value));
	}

	@Override
	public boolean exists(Predicate<? super G> predicate) {
		return false;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <H> Or<H, B> flatMap(Function<? super G, Or<H, B>> func) {
		return (Or<H, B>) this;
	}

	@Override
	public <V> V fold(Function<? super G, V> good, Function<? super B, V> bad) {
		return bad.apply(value);
	}

	@Override
	public boolean forAll(Predicate<? super G> predicate) {
		return true;
	}

	@Override
	public void forEach(Consumer<? super G> action) {
		// does nothing
	}

	@Override
	public G get() {
		throw new NoSuchElementException();
	}

	@Override
	public B getBad() {
		return value;
	}

	@Override
	public G getOrElse(G alt) {
		return alt;
	}

	@Override
	public G getOrElse(Supplier<? extends G> alt) {
		return alt.get();
	}

	@Override
	public Or<G,B> orElse(Supplier<Or<G,B>> alt) {
		return alt.get();
	}

	@Override
	public Or<G, B> recover(Function<B,G> func) {
		return Or.good(func.apply(value));
	}

	@Override
	public <C> Or<G, C> recoverWith(Function<B, Or<G, C>> func) {
		return func.apply(value);
	}

	@Override
	public Or<B, G> swap() {
		return Or.good(value);
	}

	@Override
	public Optional<G> toJavaOptional() {
		return Optional.empty();
	}

	@Override
	public Option<G> toOption() {
		return Option.none();
	}

	@Override
	public Either<B, G> toEither() {
		return new Left<>(value);
	}

	@Override
	public Try<G> toTry() {
	    if(value instanceof Throwable) 
	        return new Failure<>((Throwable) value);
	    else 
	        return new Failure<>(new IllegalArgumentException(value.toString()));
	}

	@Override
	public boolean isGood() {
		return false;
	}

	@Override
	public boolean isBad() {
		return true;
	}

	@Override
	public <H, C> Or<H, C> transform(Function<G, H> gf, Function<B, C> bf) {
		return Or.bad(bf.apply(value));
	}

	@Override
	public void forEach(Consumer<G> gc, Consumer<B> bc) {
		bc.accept(value);
	}

	@Override
	public String toString() {
		return String.format("Bad(%s)", value);
	}

	@Override
	public Or<G, B> filter(Function<G, Validation<B>> validator) {
		return this;
	}

}