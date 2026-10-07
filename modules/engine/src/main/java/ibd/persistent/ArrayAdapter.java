package ibd.persistent;

public interface ArrayAdapter<T, A> {
  int size(A array);

  T get(A array, int off);
}
