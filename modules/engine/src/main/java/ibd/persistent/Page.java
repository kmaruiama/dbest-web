package ibd.persistent;

public interface Page {
  int getPageID();

  void setPageID(int id);

  boolean isDirty();

  void setDirty(boolean dirty);
}