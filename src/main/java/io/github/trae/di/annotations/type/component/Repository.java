package io.github.trae.di.annotations.type.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specialization of {@link Component} for repository classes.
 *
 * <p>Functionally identical to {@code @Component}. Use this stereotype to
 * semantically mark classes in the data-access layer — those responsible for
 * persisting and retrieving domain objects from a backing store — distinguishing
 * them from service-layer or general-purpose components.</p>
 *
 * <pre>{@code
 * @Repository
 * public class AccountRepository extends AbstractRepository<Account, AccountProperty> {
 *     // data-access component
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Repository {
}