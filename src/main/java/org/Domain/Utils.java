package org.Domain;

import java.io.Serializable;

public class Utils {
    public static class Pair<K, V> implements Serializable {
        public final K first;
        public final V second;

        public Pair(K first, V second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public String toString() {
            return "(" + first + ", " + second + ")";
        }
    }
}
