package info.kgeorgiy.ja.elagina.arrayset;

import java.util.*;

public class ArraySet<T> extends AbstractSet<T> implements SortedSet<T> {

    private final List<T> data;
    private final Comparator<? super T> comparator;

    public ArraySet() {
        data = Collections.emptyList();
        comparator = null;
    }

    private ArraySet(Comparator<? super T> comparator) {
        this.data = Collections.emptyList();
        this.comparator = comparator;
    }

    public ArraySet(Collection<? extends T> collection) {
        data = new ArrayList<>(new TreeSet<>(collection));
        comparator = null;
    }

    public ArraySet(Collection<? extends T> collection, Comparator<? super T> comparator) {
        SortedSet<T> newSet = new TreeSet<>(comparator);
        this.comparator = comparator;
        newSet.addAll(collection);
        data = new ArrayList<>(newSet);
    }

    @Override
    public Iterator<T> iterator() {
        return Collections.unmodifiableList(data).iterator();
    }

    @Override
    public int size() {
        return data.size();
    }

    @Override
    public Comparator<? super T> comparator() {
        return comparator;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean contains(Object o) {
        return Collections.binarySearch(data, (T) Objects.requireNonNull(o), comparator) >= 0;
    }

    public int indexOf(T element) {
        int index = Collections.binarySearch(data, Objects.requireNonNull(element), comparator);

        if (index >= 0) {
            return index;
        } else {
            return -(index + 1);
        }
    }

    // remove unchecked
    @SuppressWarnings({"unchecked"})
    @Override
    public SortedSet<T> subSet(T fromElement, T toElement) {
        if (comparator != null) {
            if (comparator.compare(fromElement, toElement) > 0) {
                throw new IllegalArgumentException("fromKey > toKey");
            }
        } else if (((Comparable<T>) fromElement).compareTo(toElement) > 0) {
            throw new IllegalArgumentException("fromKey > toKey");
        }
        return new ArraySet<T>(data.subList(indexOf(fromElement), indexOf(toElement)));
    }

    @Override
    public SortedSet<T> headSet(T toElement) {
        if (isEmpty()) {
            return new ArraySet<>(data, comparator);
        }
        return new ArraySet<T>(data.subList(0, indexOf(toElement)), comparator);
    }

    @Override
    public SortedSet<T> tailSet(T fromElement) {
        if (isEmpty()) {
            return new ArraySet<>(data, comparator);
        }
        return new ArraySet<T>(data.subList(indexOf(fromElement), size()), comparator);
    }

    public T takeByIndex(int index) {
        if (index < 0 || index >= size()) {
            throw new NoSuchElementException("Cannot take this element");
        }
        return data.get(index);
    }

    @Override
    public T first() {
        return takeByIndex(0);
    }

    @Override
    public T last() {
        return takeByIndex(size() - 1);
    }
}
