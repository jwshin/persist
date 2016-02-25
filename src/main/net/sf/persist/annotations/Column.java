// $Id$

package net.sf.persist.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines column mapping for a given field. Must be added to a getter or a
 * setter of the field being mapped.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Column {

    /**
     * Name of the column mapped to the field.
     */
    String name() default ""; // a @Column annotation can leave name undefined, since it may be only concerned with autoGenerated

    /**
     * If the field is auto-generated in the database (eg. auto increment
     * fields). This will hint the engine to avoid using the field in
     * insert/update automatic operations, and to let it know which fields must
     * be updated if updateAutoGeneratedKeys is set to true.
     */
    boolean autoGenerated() default false;

}
