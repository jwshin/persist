
//$Id$

package net.sf.persist.tests.common;

import net.sf.persist.annotations.Column;
import net.sf.persist.annotations.Table;

@Table(name="simple")
public class Simple01 {

    private long id;
    private String stringCol;
    private long intCol;

    @Column(autoGenerated=true)
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    @Column(name="string_col") // valid name -- ok
    public String getStringCol() { return stringCol; }
    public void setStringCol(String stringCol) { this.stringCol = stringCol; }

    @Column(name="hello_world") // invalid name -- will blow
    public long getIntCol() { return intCol; }
    public void setIntCol(long intCol) { this.intCol = intCol; }

}

