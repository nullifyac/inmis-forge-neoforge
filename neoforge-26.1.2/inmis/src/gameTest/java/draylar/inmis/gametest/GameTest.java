package draylar.inmis.gametest;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/** Fixture metadata registered through NeoForge's native test-instance registry. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {
    String template() default "empty";
    String templateNamespace() default "inmis_game_tests";
    int timeoutTicks() default 100;
    int setupTicks() default 0;
    boolean required() default true;
}
