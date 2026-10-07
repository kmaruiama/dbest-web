package ibd.persistent;

public interface NumberArrayAdapter<N extends Number, A> extends ArrayAdapter<N, A> {
  @Override
  int size(A array);

  @Override
  N get(A array, int off);

  double getDouble(A array, int off);

  default float getFloat(A array, int off) {
    return (float) getDouble(array, off);
  }

  default int getInteger(A array, int off) {
    return (int) getLong(array, off);
  }

  default short getShort(A array, int off) {
    return (short) getLong(array, off);
  }

  long getLong(A array, int off);

  default byte getByte(A array, int off) {
    return (byte) getLong(array, off);
  }
}
