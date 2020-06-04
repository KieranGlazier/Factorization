package factorization.api.datahelpers;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds a list of types and their default values.
 */
public class UnionEnumeration {
    public static final UnionEnumeration empty = new UnionEnumeration(new Class<?>[0], new Object[0]);

    private UnionEnumeration(Class<?>[] classes, Object[] zeros) {
        if (classes.length != zeros.length) throw new IllegalArgumentException("Sizes do not match");
        this.classes = classes;
        this.zeros = zeros;
        if (classes.length > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("Too many types!");
        }
        for (int i = 0; i < classes.length; i++) {
            indexMap.put(classes[i], i);
        }
        DataValidator data = new DataValidator(new HashMap<String, Object>());
        for (int i = 0; i < classes.length; i++) {
            try {
                data.as(Share.VISIBLE_TRANSIENT, "#" + i).putUnion(this, zeros[i]);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    public static UnionEnumeration build(Object ...parts) {
        if (parts.length % 2 != 0) throw new IllegalArgumentException("Not pairs");
        Class<?> classes[] = new Class<?>[parts.length / 2];
        Object zeros[] = new Object[parts.length / 2];
        for (int i = 0; i < parts.length; i += 2) {
            Class<?> klass = (Class<?>) parts[i];
            Object val = parts[i + 1];
            if (val != null && !klass.isInstance(val)) throw new IllegalArgumentException("default value does not match class");
            classes[i / 2] = klass;
            zeros[i / 2] = val;
            if (val == null ^ klass == Void.TYPE) {
                throw new IllegalArgumentException("nulls must correspond to Voids.");
            }
        }
        return new UnionEnumeration(classes, zeros);
    }

    public UnionEnumeration extend(Object ...parts) {
        int front = classes.length + zeros.length;
        int len = front + parts.length;
        Object[] x = new Object[len];
        int i;
        for (i = 0; i < front; i++) {
            x[i] = (i % 2 == 0) ? classes[i / 2] : zeros[i / 2];
        }
        for (; i < x.length; i++) {
            x[i] = parts[i - front];
        }
        return UnionEnumeration.build(x);
    }


    final Class<?> classes[];
    final Object zeros[];
    final Map<Class<?>, Integer> indexMap = new HashMap<Class<?>, Integer>();

    public byte getIndex(Object val) {
        Class k = val.getClass();
        Integer integer;
        while (true) {
            if (k == null) {
                throw new IllegalArgumentException("Type is not registered to be serialized: " + val + ", a " + val.getClass());
            }
            integer = indexMap.get(k);
            if (integer != null) break;
            k = k.getSuperclass();
        }
        return (byte) (int) integer;
    }

    public Object byIndex(byte b) {
        return zeros[b];
    }

    public Class<?> classByIndex(byte index) {
        return classes[index];
    }
}
