package ibd.persistent;

import java.io.DataInput;
import java.io.DataOutput;

import java.io.IOException;

public abstract class AbstractExternalizablePage implements ExternalizablePage, Page {
  private static final long serialVersionUID = 2;

  private int id;

  private transient boolean dirty;
  
  
  protected int usedSpace;
  
  public AbstractExternalizablePage() {
    super();
    this.id = -1;
    this.usedSpace = 0;
  }

  public int getUsedSpace() {
    return usedSpace;
  }
  
  public int getHeaderSizeInBytes() {
    return 2 * Integer.BYTES;
  }
  
  
  @Override
  public final int getPageID() {
    return id;
  }

  @Override
  public final void setPageID(int id) {
    this.id = id;
  }

  @Override
  public final boolean isDirty() {
    return dirty;
  }

  @Override
  public final void setDirty(boolean dirty) {
    this.dirty = dirty;
  }

  @Override
  public void writeExternal(DataOutput out) throws IOException {
    out.writeInt(id);
    out.writeInt(usedSpace);
  }

  @Override
  public void readExternal(DataInput in) throws IOException {
    id = in.readInt();
    usedSpace = in.readInt();
  }
  
  @Override
  public int getSizeInBytes(){
      return -1;
  }

  @Override
  public String toString() {
    return Integer.toString(id);
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o != null && getClass() == o.getClass()
        && id == ((AbstractExternalizablePage) o).getPageID());
  }

  @Override
  public int hashCode() {
    return id;
  }
}
