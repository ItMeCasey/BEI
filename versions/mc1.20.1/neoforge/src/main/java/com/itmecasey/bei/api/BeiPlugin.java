package com.itmecasey.bei.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a BEI plugin. The class must implement {@link IBeiPlugin} and have a public
 * constructor with no parameters. BEI finds it by scanning every mod's classes, so a mod needs no
 * call into BEI and can ship the plugin even when BEI is not installed (the annotation is simply
 * never read). Put {@code value} to the id of another mod when the plugin only makes sense with
 * that mod: the plugin is then only loaded when that mod is installed.
 *
 * <p>Plugins run on the client only, so they may use client classes freely.
 *
 * <pre>{@code
 * @BeiPlugin("mymachines")
 * public class MyPlugin implements IBeiPlugin {
 *     @Override
 *     public void registerCategories(ICategoryRegistration registration) { ... }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BeiPlugin {
    /** The id of a mod that must be installed for the plugin to load; empty for none. */
    String value() default "";
}
