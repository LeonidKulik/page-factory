package ru.sbtqa.tag.pagefactory.maven_artefacts.module_entry_points.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * WebElement`s title. Optional annotation.
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ElementTitle {

    /**
     * Title text
     *
     * @return a {@link java.lang.String} object.
     */
    public String value();
    }